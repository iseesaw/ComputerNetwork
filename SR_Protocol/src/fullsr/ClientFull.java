package fullsr;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Kaiyan.Zh
 * @create 2018-10-30 10:01
 * @Description full duplex sr client
 */

public class ClientFull {

    /* 服务器运行端口和IP地址 */
    private final int SERVER_PORT = 10240;
    private final String SERVER_IP = "127.0.0.1";
    /* 服务器IP地址 */

    private InetAddress SERVER_IPAddress;
    /* 客户套接字 */
    private DatagramSocket clientSocket;

    /* 缓存最大长度 */
    private final int BUFFER_LENGTH = 1026;
    /* 接收缓存 */
    private byte[] receiveBuff;
    /* 发送缓存 */
    private byte[] sendBuff;

    /* 接收方维护一个接收窗口 ||不可用||未收到|已收到|未收到|可用||不可用|| */
    private final int SEQ_SIZE = 20;
    private final int RECEIVE_WIND_SIZE = 10;
    /* 期望收到的数据包序列号 */
    private int waitSeq;
    /* 保存请求的文件, 需要在接收状态码99时初始化 */
    private byte[] dataFromServer;
    /* 已经收到的数据的总大小, 用以保存时进行偏移 */
    private int alreadyReceiveDataLength;

    /* 缓存收到的数据报 */
    /* 缓存收到的字节数据、最大缓存为窗口大小 */
    private byte[][] cacheBuff;
    /* 序列号和缓存中的对应下标 */
    HashMap<Integer, Integer> seqMapIndexInCache;
    /* 剩余的缓存下标 */
    Stack<Integer> cacheBuffIndexs;

    /* 接收数据包 */
    private DatagramPacket receivePacket;
    /* 发送数据包 */
    private DatagramPacket sendPacket;

    /* 数据包丢失率 */
    private double packetLossRatio = 0;
    /* ack丢失率 */
    private double ackLossRatio = 0;

    /* 向服务器发送数据 */
    private byte[] dataToServer;
    /* 收到的ack */
    private boolean[] ack;
    /* 总数据包数 */
    private int totalPacket;
    /* 已经发送的总数据包数 */
    private int totalSeq;
    /* 已经正确被接收的包数, 返回ack则加1 */
    private int totalBeenReceived;
    /* SR 为每个分组准备一个计时器 */
    int[] timers;
    /* 当前数据包的seq */
    private int curSeq;
    /* 当前等待确认的ack */
    private int curAck;
    /* 缓存已发送的包 */
    private byte[][] sendCacheBuff;
    /* 序列号和下标对应 */
    private HashMap<Integer, Integer> seqForCacheBuffIndexs;
    /* 可用下标 */
    private Stack<Integer> sendcacheBuffIndexs;


    public ClientFull() throws Exception {
        /* 建立客户套接字, 系统自动绑定端口和IP地址等信息 */
        this.clientSocket = new DatagramSocket();
        /* DNS查询 */
        this.SERVER_IPAddress = InetAddress.getByName(this.SERVER_IP);
        /* 初始接收和发送缓冲区 */
        this.receiveBuff = new byte[this.BUFFER_LENGTH];
        this.sendBuff = new byte[this.BUFFER_LENGTH];
        /* 运行客户端 */
        this.run();
    }

    /**
     * 显示用户操作提示
     */
    public void printTips() {
        System.out.println("欢迎使用SR传输测试客户端...");
        System.out.println("可选指令: ");
        System.out.println("    -time 获取服务器系统时间");
        System.out
          .println("    -testsr[a][b] 进行SR单向传输测试, r表示 a、b为0-1之间的小数, 分别表示数据包丢失率和ack丢失率");
        System.out
          .println("    -testfsr[a][b] 进行SR双向传输测试, r表示 a、b为0-1之间的小数, 分别表示数据包丢失率和ack丢失率");
        System.out.println("    -quit 退出");
    }

    /**
     * 运行客户端
     * while.
     * First. 发送-time 测试和服务器的连接
     * Second. 向服务器请求文件
     * while. 维护接收窗口.
     * 收到期待的序列号的分组, 则更新并发送ack
     * -lossRatio
     * 使用一定丢包率丢弃收到的分组
     * 使用一定ack丢失率不发送ack
     * Third. Good Bye!
     */
    public void run() throws IOException {
        boolean isQuitClient = false;
        /* 接收用户输入 */
        Scanner in = new Scanner(System.in);
        /* 用户输入 */
        String cmd;
        /* 对服务器的请求 */
        String request;
        while (!isQuitClient) {
            /* 输出操作提示 */
            printTips();
            cmd = in.nextLine();
            /* 获取服务器时间 */
            if (cmd.equals("-time")) {
                request = "-time";

                /* 向服务器发送 */
                this.sendToServer(request.getBytes(), request.getBytes().length, false);
                /* 接收服务器响应 */
                this.receiveFromServer(false);

                /* 输出 */
                System.out.print("服务器系统时间为 ");
                System.out.println(
                  new String(Arrays.copyOf(this.receiveBuff, this.receivePacket.getLength())));

            } else if (cmd.contains("-testsr[")) {
                /* 指令正则式 */
                String cmdRegex = "^-testsr\\[0\\.\\d+\\]\\[0.\\d+\\]$";
                Pattern cmdPattern = Pattern.compile(cmdRegex);
                Matcher cmdMatcher = cmdPattern.matcher(cmd);
                if (cmdMatcher.find() == false) {
                    System.out.println("指令不合法, 请重新输入...");
                } else {
                    /* 丢失率正则 */
                    String lossRegex = "0\\.\\d+";
                    Pattern lossPattern = Pattern.compile(lossRegex);
                    Matcher lossMatcher = lossPattern.matcher(cmd);
                    /* 获取丢包率 */
                    lossMatcher.find();
                    this.packetLossRatio = Double.valueOf(lossMatcher.group());
                    /* 获取ack丢失率 */
                    lossMatcher.find();
                    this.ackLossRatio = Double.valueOf(lossMatcher.group());

                    /* 启动GBN测试程序 */
                    this.testGBN(false);
                }

            } else if (cmd.contains("-testfsr")) {
                /* 指令正则式 */
                String cmdRegex = "^-testfsr\\[0\\.\\d+\\]\\[0.\\d+\\]$";
                Pattern cmdPattern = Pattern.compile(cmdRegex);
                Matcher cmdMatcher = cmdPattern.matcher(cmd);
                if (cmdMatcher.find() == false) {
                    System.out.println("指令不合法, 请重新输入...");
                } else {
                    /* 丢失率正则 */
                    String lossRegex = "0\\.\\d+";
                    Pattern lossPattern = Pattern.compile(lossRegex);
                    Matcher lossMatcher = lossPattern.matcher(cmd);
                    /* 获取丢包率 */
                    lossMatcher.find();
                    this.packetLossRatio = Double.valueOf(lossMatcher.group());
                    /* 获取ack丢失率 */
                    lossMatcher.find();
                    this.ackLossRatio = Double.valueOf(lossMatcher.group());

                    /* 启动GBN测试程序 */
                    this.testGBN(true);
                }

            } else if (cmd.equals("-quit")) {
                request = "-quit";

                /* 向服务器发送 */
                this.sendToServer(request.getBytes(), request.getBytes().length, false);
                /* 接收服务器响应 */
                this.receiveFromServer(false);

                /* 输出 */
                System.out.print("服务器回复 ");
                System.out.println(
                  new String(Arrays.copyOf(this.receiveBuff, this.receivePacket.getLength())));

                System.out.println("退出客户端...");
                isQuitClient = true;
            } else {
                System.out.println("指令不合法, 请重新输入...");
            }

        }

    }

    /**
     * 进行GBN传输测试
     * s1. 发送请求
     * s2. 进行握手阶段, 接收状态码99, 回复状态码100
     * s3. 进入GBN传输阶段
     * （使用丢包率判断是否丢失）
     * 接收期望的数据包, 回复ack（使用ack丢失率判断是否不回复）
     * 接收非期望的数据包, 使用接收窗口保存
     */
    public void testGBN(boolean isFullDuplex) throws IOException {
        /* 发送测试请求 */
        String request;
        /* 进行测试标识 */
        boolean runFlagR = true;
        boolean runFlagS = isFullDuplex;
        if ((isFullDuplex)) {
            request = "-testfsr";
        } else {
            request = "-testsr";
        }

        /* 发送请求 */
        this.sendToServer(request.getBytes(), request.getBytes().length, false);

        /* 标识第一个的分组是否到达 */
        boolean isFirstArrived = false;


        /* 状态标识 */
        int stage = 1;
        while (runFlagR || runFlagS) {
            switch (stage) {
                /* 等待握手阶段 */
                case 1: {
                    boolean response = this.receiveFromServer(false);
                    /* 判断是否丢失 */
                    if (response) {
                        /* 判断状态码 */
                        if (this.receiveBuff[0] == 99) {
                            System.out.println("收到服务器确认状态码99...");
                            System.out.println("准备回复服务器状态码100...");

                            /* 发送100确认码 */
                            this.sendBuff[0] = 100;
                            if (isFullDuplex) {

                                /* 读取准备发送的文件 */
                                this.dataToServer = Files
                                  .readAllBytes(Paths.get("Files/client.png"));
                                /* 向客户端发送请求的文件大小 */
                                int bits = this.dataToServer.length / 100;
                                /* 每100位保存在99状态码的后面 */
                                for (int i = 1; i <= bits; i++) {
                                    this.sendBuff[i] = (byte) 100;
                                }
                                this.sendBuff[bits + 1] = (byte) (this.dataToServer.length
                                  - bits * 100);
                                this.sendBuff[bits + 2] = '\0';
                            } else {
                                this.sendBuff[1] = '\0';
                            }

                            boolean result = this
                              .sendToServer(this.sendBuff, this.BUFFER_LENGTH, false);

                            if (!result) {
                                System.out.println("回复服务器状态码100丢失...");
                            } else {
                                System.out.println("回复服务器状态码100成功...");
                                /* 成功发送状态码100, 进行相关初始化并准备接收数据 */
                                /* 解析文件大小 */
                                int fileSize = 0;
                                int index = 1;
                                while (this.receiveBuff[index] != '\0') {
                                    fileSize += this.receiveBuff[index];
                                    index++;
                                }
                                System.out.println("即将准备接收数据, 共" + fileSize + "B...");
                                /* 保存接收的文件 */
                                this.dataFromServer = new byte[fileSize];
                                /* 初始化收到的数据大小 */
                                this.alreadyReceiveDataLength = 0;
                                /* 初始化等待序列号 */
                                this.waitSeq = 1;

                                /* 初始化缓存 */
                                this.cacheBuff = new byte[this.RECEIVE_WIND_SIZE][];
                                /* 初始化序列号和缓存对应关系 */
                                this.seqMapIndexInCache = new HashMap<>();
                                /* 初始化可用缓存下标 */
                                this.cacheBuffIndexs = new Stack<>();
                                for (int i = 0; i < this.RECEIVE_WIND_SIZE; i++) {
                                    this.cacheBuffIndexs.push(i);
                                }

                                /* 全双工 */
                                if (isFullDuplex) {

                                    this.totalPacket = (int) Math
                                      .ceil(
                                        (double) this.dataToServer.length / (this.BUFFER_LENGTH
                                          - 2));
                                    this.totalSeq = 0;
                                    System.out
                                      .println("准备发送数据, 共" + this.dataFromServer.length + "B");
                                    /* 初始收到的ack以及发送的序列号等 */
                                    this.curAck = 41;
                                    this.curSeq = 41;
                                    /* 当前已经被发送的总报文数 */
                                    this.totalBeenReceived = 0;
                                    /* 初始化收到的ack数组 */
                                    this.ack = new boolean[this.SEQ_SIZE];
                                    for (int i = 0; i < this.SEQ_SIZE; i++) {
                                        this.ack[i] = true;
                                    }
                                    /* 初始化定时器 */
                                    this.timers = new int[this.SEQ_SIZE];
                                    /* 初始缓存 */
                                    this.sendCacheBuff = new byte[this.SEQ_SIZE][];
                                    this.seqForCacheBuffIndexs = new HashMap<>();
                                    this.sendcacheBuffIndexs = new Stack<>();
                                    for (int i = 0; i < this.SEQ_SIZE; i++) {
                                        this.sendcacheBuffIndexs.push(i);
                                    }
                                }
                                /* 进入数据接收阶段 */
                                stage = 2;
                            }
                        }
                    } else {
                        System.out.println("接收状态码99丢失...");
                    }
                    break;
                }
                /* 数据接收阶段 */
                case 2: {
                    if (this.seqIsAvailable()) {
                        /* 对sendBuff进行打包, 并装载进sendPacket */
                        this.makePacket();

                        System.out.println("发送数据报: " + this.curSeq);
                        /* 发送数据报后需要做相关处理 */
                        /* 设置发送的序列号的确认状态 */
                        this.ack[this.curSeq - 40 - 1] = false;
                        /* 设置该序列号的超时设置 */
                        this.timers[this.curSeq - 40 - 1] = 0;

                        /* 当前序列号加1 */
                        this.curSeq++;
                        /* 防止序列号超过总序列号 */
                        if (this.curSeq == 61) {
                            this.curSeq = 41;
                        }
                        //this.curSeq %= this.SEQ_SIZE;
                        /* 当前已发送数据包总序列号加1 */
                        this.totalSeq++;

                        /* 发送数据 */
                        this.clientSocket.send(this.sendPacket);

                    }

                    try {
                        if (isFullDuplex) {
                            this.clientSocket.setSoTimeout(100);
                        }
                        /* 准备接收数据 */
                        boolean response = this.receiveFromServer(true);
                        /* 成功收到 */
                        if (response) {
                            /* 获取收到的序列号 */
                            int seq = this.receiveBuff[0];
                            /* 收到的数据包大小 */
                            int length = this.receivePacket.getLength();
                            if (seq == 102) {
                                System.out.println("已经发送完所有数据");
                                runFlagS = false;
                            }

                            if (seq == 101) {
                                System.out.println("已经接收所有数据");
                                runFlagR = false;
                            }

                            /* 客户端发送数据, 处理收到的ack */
                            if (seq >= 40 && seq <= 61) {
                                this.ackHandler(this.receiveBuff[0]);

                                this.notReceive();
                                /* 判断是否已经成功发送所有的数据报被接收 */
                                if (this.totalPacket == this.totalBeenReceived) {
                                    System.out.println("已经成功发送所有的数据报");
                                    this.sendBuff[0] = 102;
                                    this.sendBuff[1] = '\0';
                                    this.sendToServer(this.sendBuff, 2, false);
                                    runFlagS = false;
                                }



                                /* 客户端接收数据, 向服务器发送ack */
                            } else if (seq != 102) {
                                /* 是期待的序列号, 检查缓存, 进行累计确认 */
                                if (this.waitSeq == seq) {
                                    /* 标识第一个分组已经到达, 避免后面的发送最小为到达时出现异常 */
                                    isFirstArrived = true;

                                    //TODO 不能直接用\0判断, 传输的数据中可能使用了\0
                                    /* 提取中间的数据部分保存到data中 */
                                    for (int i = 1; i < this.receivePacket.getLength() - 1; i++) {
                                        this.dataFromServer[i + this.alreadyReceiveDataLength
                                          - 1] = this.receiveBuff[i];
                                    }
                                    /* 更新收到的数据总大小 */
                                    this.alreadyReceiveDataLength =
                                      this.alreadyReceiveDataLength + (
                                        this.receivePacket.getLength()
                                          - 2);

                                    /* 启动累积确认机制, 检查缓存并发送ack, 以及更新期待的seq */
                                    this.accumulateACK();

                                    /* 文件总大小判断 */
                                    if (this.alreadyReceiveDataLength
                                      == this.dataFromServer.length) {
                                        System.out
                                          .println(
                                            "请求数据已接受完毕, 共" + this.alreadyReceiveDataLength
                                              + "B...");
                                        runFlagR = false;

                                        /* TODO ack丢失, 但所有数据都已经收到,客户端关闭,但发送方会一直发送没有收到ack的分组, 因此需要回复101表示结束通信 */
                                        this.sendBuff[0] = 101;
                                        this.sendBuff[1] = '\0';
                                        this.sendToServer(this.sendBuff, 2, false);
                                    }


                                    /* 不是期待的序列号 - 提前到达、缓存中仍有空闲则保存, 否则直接丢弃 */
                                } else {
                                    /* 计算收到序列号和当前期待序列号的间隔 */
                                    int step = this.receiveBuff[0] - this.waitSeq;
                                    /* 比期待的序列号大, 且缓冲中仍有空闲, 保存并回复ack */
                                    // TODO 条件重复？？？
                                    if (((step > 0 && step <= 10) || (step < 0 && step > 10))
                                      && this.seqMapIndexInCache.size() <= this.RECEIVE_WIND_SIZE) {
                                        /* 找看空闲的缓存下标 */
                                        int index = this.cacheBuffIndexs.pop();
                                        /* 保存序列号和对应缓存中的下标 */
                                        this.seqMapIndexInCache.put(seq, index);
                                        /* 保存缓存 */
                                        /* 不能直接等于receiveBuff, 会地址相等, 缓存会随之receiveBuff改变 */
                                        this.cacheBuff[index] = new byte[length];
                                        for (int i = 0; i < length; i++) {
                                            this.cacheBuff[index][i] = this.receiveBuff[i];
                                        }
                                        System.out
                                          .println("分组" + this.receiveBuff[0] + "提前到达, 放入缓存");
                                        /* 回复收到的ack, 但期待的ack并不改变 */
                                        this.sendBuff[0] = this.receiveBuff[0];
                                        this.sendBuff[1] = '\0';
                                        this.sendToServer(this.sendBuff, 2, true);
                                        /* 不做相关更新 */


                                        /* 比期待的序列号小或缓存中没有空闲直接丢弃 */
                                    } else if ((step < 0 && -step <= 10) || (step > 0
                                      && step > 10)) {
                                        System.out.println("收到之前已经收到的分组" + this.receiveBuff[0]);
                                        this.sendBuff[0] = this.receiveBuff[0];
                                        this.sendBuff[1] = '0';
                                        this.sendToServer(this.sendBuff, 2, true);
                                    } else {
                                        System.out.println("丢弃分组" + this.receiveBuff[0]);
//                                        this.sendBuff[0] = this.receiveBuff[0];
//                                        this.sendBuff[1] = '0';
//                                        this.sendToServer(this.sendBuff, 2, true);

                                    }

                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("请求超时...");
                    } finally {

                    }

                    break;
                }

            }
        }
        if (isFullDuplex) {
            Files.write(Paths.get("Files/clientFromServer.png"),
              this.dataFromServer);
            System.out.println("保存在Files/clientFromServer.png");
        } else {
            Files.write(Paths.get("Files/client.png"),
              this.dataFromServer);
            System.out.println("保存在Files/client.png");
        }
    }

    public boolean seqIsAvailable() {
        /* 已经发送完所有报文 */
        if (this.totalSeq == this.totalPacket) {
            return false;
        }
        /* 计算窗口大小 */
        int step = this.curSeq - this.curAck;
        /* 包括 curSeq > curAck and curSeq < curAck两种情况 */
        step = step >= 0 ? step : this.SEQ_SIZE + step;
        /* 计算发送但未收到ack的报文数 */
        if (step >= this.RECEIVE_WIND_SIZE) {
            return false;
        }

        /* 已经被接收了, 可以使用该序列号, TODO 多此一举？？？ */
        if (this.ack[this.curSeq - 40 - 1]) {
            return true;
        }

        return true;

    }

    /**
     * 将数据打包装载到sendBuff中
     * 根据 curSeq totalSeq totalPacket判断装载大小
     *
     * 装载包括首位序列号 + 数据 + EOF
     */
    public void makePacket() {
        /* 传输data偏移 */
        int offset = this.totalSeq * (this.BUFFER_LENGTH - 2);
        /* 装载的数据长度 */
        int length = this.BUFFER_LENGTH - 2;
        /* 特殊处理最后一个不足一个数据帧的剩余数据 */
        if (this.totalSeq == this.totalPacket - 1) {

            length = this.dataToServer.length - this.totalSeq * (this.BUFFER_LENGTH - 2);
        }
        /* 首位填入当前序列号, 序列号从1开始 */
        this.sendBuff[0] = (byte) this.curSeq;

        /* 装载数据 */
        for (int i = 1; i <= length; i++) {
            this.sendBuff[i] = this.dataToServer[offset + i - 1];
        }
        /* 设置最后一位为0 */
        // TODO 最后一位设置EOF？？？
        this.sendBuff[length + 1] = '\0';
        /* 将sendBuff装载到sendPacket中, 长度为length+2 */
        this.sendPacket = new DatagramPacket(this.sendBuff, length + 2, this.SERVER_IPAddress,
          this.SERVER_PORT);

        /* 更新发送缓存 */
        int cacheIndex = this.sendcacheBuffIndexs.pop();
        this.seqForCacheBuffIndexs.put(this.curSeq, cacheIndex);
        this.sendCacheBuff[cacheIndex] = new byte[length + 2];
        for (int i = 0; i < length + 2; i++) {
            this.sendCacheBuff[cacheIndex][i] = this.sendBuff[i];
        }

    }

    /**
     * ack的累积确认, 收到了期待的ack
     * 然后检查缓存, 是否存在之后紧接着的序列号, 一起回复
     */
    public void accumulateACK() throws IOException {
        /* 发送收到的序列号 */
        this.sendBuff[0] = (byte) this.waitSeq;
        this.sendBuff[1] = '\0';
        this.sendToServer(this.sendBuff, this.BUFFER_LENGTH, true);

        /* 检查缓存中已接收紧接着期待ack的序列号 */
        int existSeq = 0;
        for (int i = 1; i <= this.RECEIVE_WIND_SIZE; i++) {
            if (this.seqMapIndexInCache.containsKey(this.waitSeq + i)) {
                existSeq++;
            } else {
                break;
            }
        }
        /* 缓存中存在, 从缓存中读取并更新数据 */
        if (existSeq > 0) {
            /* 读取缓存 */
            for (int i = 1; i <= existSeq; i++) {
                this.waitSeq++;
                /* 防止溢出 */
                if (this.waitSeq == 21) {
                    this.waitSeq = 1;
                }
                /* 序列号在缓存中对于的下标 */
                int index = this.seqMapIndexInCache.get(this.waitSeq);
                /* 读取 */
                for (int j = 0; j < this.cacheBuff[index].length - 2; j++) {
                    this.dataFromServer[this.alreadyReceiveDataLength + j] = this.cacheBuff[index][j
                      + 1];
                }
                /* 更新可用缓存下标 */
                this.cacheBuffIndexs.push(index);
                /* 删除缓存下标和序列号对于关系, 即使不删除, 后面添加时很也会更新 */
                this.seqMapIndexInCache.remove(this.waitSeq);
                /* 更新收到数据的长度 */
                this.alreadyReceiveDataLength =
                  this.alreadyReceiveDataLength + this.cacheBuff[index].length - 2;

            }
        }

        /* 下一个期待的序列号 */
        this.waitSeq++;
        if (this.waitSeq == 21) {
            this.waitSeq = 1;
        }

    }

    /**
     * 发送数据data给服务器
     * 使用生成随机数的方式判断是否丢弃分组
     *
     * @param data 数据字节数组
     * @param length 数据长度
     * @param isLoss 是否允许出现丢失, 允许ack出现丢失和状态码100丢失
     * @return false if loss otherwise true
     */
    public boolean sendToServer(byte[] pktData, int length, boolean isLoss) throws IOException {
        /* 模拟分组丢失 */
        double rand = Math.random();
        if (rand < this.packetLossRatio && isLoss) {
            System.out.println("ack丢失: " + pktData[0]);
            return false;
        }

        /* 封装数据包 */
        this.sendPacket = new DatagramPacket(pktData, length, this.SERVER_IPAddress,
          this.SERVER_PORT);
        /* 发送 */
        this.clientSocket.send(this.sendPacket);
        if (isLoss) {
            System.out.println("成功发送ack: " + pktData[0]);
        }
        return true;
    }

    /**
     * 从服务器接收
     *
     * @param isLoss 是否允许丢失, 允许GBN测试的丢失
     * @return false if loss, otherwise true
     */
    public boolean receiveFromServer(boolean isLoss) throws IOException {
        /* 接收数据包 */
        this.receivePacket = new DatagramPacket(this.receiveBuff, this.BUFFER_LENGTH);
        this.clientSocket.receive(this.receivePacket);
        /* 模拟随机丢包 */
        double rand = Math.random();
        if ((this.receiveBuff[0] > 40 && this.receiveBuff[0] <= 61) || this.receiveBuff[0] == 102
          || this.receiveBuff[0] == 101) {
            System.out.println("成功收到ack" + this.receiveBuff[0]);
            return true;
        }
        if (rand < this.packetLossRatio && isLoss) {
            System.out.println("数据包丢失: " + this.receiveBuff[0]);
            return false;
        }
        if (isLoss && this.receiveBuff[0] < 21) {
            System.out.println("成功接收数据包: " + this.receiveBuff[0]);
        }
        return true;
    }

    public void ackHandler(byte receiveAck) {

        /* SR只对收到的ack进行确定 */
        System.out.println("收到ack: " + receiveAck);
        /* 标识已经收到 */
        this.ack[receiveAck - 40 - 1] = true;

        /* TODO 巨坑 receiveAck和curAck都是1, 但是类型不同所以hash不在同一位置？？？艹 */
        int index = this.seqForCacheBuffIndexs.get(new Integer(receiveAck));
        /* 释放占用的缓存 */
        this.seqForCacheBuffIndexs.remove(new Integer(receiveAck));
        this.sendcacheBuffIndexs.push(index);
        this.totalBeenReceived++;
        // 更新窗口
        if (this.curAck == receiveAck) {
            this.curAck++;
        }
        /* 滑动窗口 */
        if (this.curSeq >= this.curAck) {
            for (int i = this.curAck; i < this.curSeq; i++) {
                if (this.ack[i - 40 - 1]) {
                    this.curAck++;
                } else {
                    break;
                }
            }
        } else {
            for (int i = this.curAck; i <= 60; i++) {
                if (this.ack[i - 40 - 1]) {
                    this.curAck++;
                } else {
                    break;
                }
            }
            if (this.curAck == 61) {
                this.curAck = 41;
                for (int i = 1; i < this.curSeq; i++) {
                    if (this.ack[i - 40 - 1]) {
                        this.curAck++;
                    } else {
                        break;
                    }
                }
            }


        }
    }

    public void notReceive() throws IOException {
        /* 没有收到ack, 为每个发送还没确认的seq计时器加1 */
        if (this.curSeq >= this.curAck) {
            for (int i = this.curAck - 1; i < this.curSeq; i++) {
                /* 没有收到 */
                if (!this.ack[i - 40]) {
                    this.timers[i - 40]++;
                    /* 超时重传 */
                    if (this.timers[i - 40] > 20) {
                        this.timeoutHandler(i + 1);
                    }
                }
            }
        } else {
            /* 窗口不在正中间 */
            for (int i = this.curAck - 1; i < 20; i++) {
                /* 没有收到 */
                if (!this.ack[i - 40]) {
                    this.timers[i - 40]++;
                    /* 超时处理 */
                    if (this.timers[i - 40] > 20) {
                        this.timeoutHandler(i + 1);
                    }
                }
            }
            for (int i = 0; i < this.curSeq; i++) {
                /* 没有收到 */
                if (!this.ack[i - 40]) {
                    this.timers[i - 40]++;
                    /* 超时处理 */
                    if (this.timers[i - 40] > 20) {
                        this.timeoutHandler(i + 1);
                    }
                }
            }
        }


    }


    public void timeoutHandler(int seq) throws IOException {

        //SR 超时选择重传超时的序列号
        System.out.println("ack" + seq + "超时, 准备重传");
        this.ack[seq - 40 - 1] = false;
        this.timers[seq - 40 - 1] = 0;
        int cacheIndex = this.seqForCacheBuffIndexs.get(seq);
        /* 发送该超时的seq */
        this.sendPacket = new DatagramPacket(this.sendCacheBuff[cacheIndex],
          this.sendCacheBuff[cacheIndex].length, this.SERVER_IPAddress, this.SERVER_PORT);
        this.clientSocket.send(this.sendPacket);


    }

    public static void main(String[] args) throws Exception {
        ClientFull client = new ClientFull();
        client.run();
    }
}
