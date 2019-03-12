package sr;

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
 * @create 2018-10-27 10:51
 * @Description GBN client
 */
public class Client {

    /* 服务器运行端口和IP地址 */
    private final int SERVER_PORT = 10240;
    private final String SERVER_IP = "127.0.0.1";
    /* 服务器IP地址 */

    private InetAddress IPAddress;
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

    public Client() throws Exception {
        /* 建立客户套接字, 系统自动绑定端口和IP地址等信息 */
        this.clientSocket = new DatagramSocket();
        /* DNS查询 */
        this.IPAddress = InetAddress.getByName(this.SERVER_IP);
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
          .println("    -testsr[a][b] 进行SR传输测试, r表示 a、b为0-1之间的小数, 分别表示数据包丢失率和ack丢失率");
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

            } else if (cmd.contains("-testsr")) {
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
                    this.testGBN();
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
    public void testGBN() throws IOException {
        /* 发送测试请求 */
        String request = "-testsr";
        /* 发送请求 */
        this.sendToServer(request.getBytes(), request.getBytes().length, false);

        /* 标识第一个的分组是否到达 */
        boolean isFirstArrived = false;

        /* 进行测试标识 */
        boolean runFlag = true;
        /* 状态标识 */
        int stage = 1;
        while (runFlag) {
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
                            this.sendBuff[1] = '\0';
                            boolean result = this.sendToServer(this.sendBuff, 2, false);

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
                    /* 准备接收数据 */
                    boolean response = this.receiveFromServer(true);
                    /* 成功收到 */
                    if (response) {
                        /* 获取收到的序列号 */
                        int seq = this.receiveBuff[0];
                        /* 收到的数据包大小 */
                        int length = this.receivePacket.getLength();
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
                              this.alreadyReceiveDataLength + (this.receivePacket.getLength() - 2);

                            /* 启动累积确认机制, 检查缓存并发送ack, 以及更新期待的seq */
                            this.accumulateACK();

                            /* 文件总大小判断 */
                            if (this.alreadyReceiveDataLength == this.dataFromServer.length) {
                                System.out
                                  .println("请求数据已接受完毕, 共" + this.alreadyReceiveDataLength + "B...");
                                Files.write(Paths.get("Files/client.png"), this.dataFromServer);
                                System.out.println("保存在Files/client.png");
                                runFlag = false;

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
                              && this.seqMapIndexInCache.size() <= this.RECEIVE_WIND_SIZE
                              && !this.cacheBuffIndexs.empty()) {
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
                                System.out.println("分组" + this.receiveBuff[0] + "提前到达, 放入缓存");
                                /* 回复收到的ack, 但期待的ack并不改变 */
                                this.sendBuff[0] = this.receiveBuff[0];
                                this.sendBuff[1] = '\0';
                                this.sendToServer(this.sendBuff, 2, true);
                                /* 不做相关更新 */


                                /* 比期待的序列号小或缓存中没有空闲直接丢弃 */
                            } else if ((step < 0 && -step <= 10) || (step > 0 && step > 10)) {
                                System.out.println("收到之前已经收到的分组" + this.receiveBuff[0]);
                                this.sendBuff[0] = this.receiveBuff[0];
                                this.sendBuff[1] = '0';
                                this.sendToServer(this.sendBuff, 2, true);
                            } else {
                                System.out.println("丢弃分组" + this.receiveBuff[0]);
                            }

//                            //GBN的直接丢弃处理
//                            /* 考虑回复 */
//                            /* 已经到达的直接丢弃, 发送已经接收的最小的序列号 */
//                            /* 如果第一个分组没有到达, 但是后面的到达的, 则不回复 */
//                            if (isFirstArrived) {
//                                if (this.waitSeq > 1) {
//                                    this.sendBuff[0] = (byte) (this.waitSeq - 1);
//                                } else {
//                                    this.sendBuff[0] = (byte) this.SEQ_SIZE;
//                                }
//                                this.sendBuff[1] = '\0';
//                                this.sendToServer(this.sendBuff, 2, true);
//                            }
                        }
                    }
                    break;
                }

            }
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
        this.sendPacket = new DatagramPacket(pktData, length, this.IPAddress, this.SERVER_PORT);
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
        if (rand < this.packetLossRatio && isLoss) {
            System.out.println("数据包丢失: " + this.receiveBuff[0]);
            return false;
        }
        if (isLoss) {
            System.out.println("成功接收数据包: " + this.receiveBuff[0]);
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        Client client = new Client();
        client.run();
    }
}
