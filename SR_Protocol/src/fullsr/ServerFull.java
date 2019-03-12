package fullsr;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Stack;

/**
 * @author Kaiyan.Zh
 * @create 2018-10-30 10:02
 * @Description full duplex sr server
 */
public class ServerFull {

    /* 服务器运行端口号 */
    private int SERVER_PORT = 10240;
    /* 服务器运行IP地址 */
    private String SERVER_IP = "127.0.0.1";
    /* 用户端口和ip地址 */
    /* 接收缓存 */
    private byte[] receiveBuff;
    /* 发送缓存 */
    private byte[] sendBuff;

    private int CLIENT_PORT;
    private InetAddress CLIENT_IP;
    /* 缓冲区大小, 数据包格式为 |Seq(1B)|Data(1024B)|0(1B)| */
    private final int BUFFER_LENGTH = 1026;
    /* 发送方窗口大小 */
    private final int SEND_WIND_SIZE = 10;
    /* 序列号个数, 接收序列号为1-20, 0表示发送失败 */
    private final int SEQ_SIZE = 20;
    /* 超时时间设置 */
    private final int TIMEOUT = 100;
    /* 保存收到的ack */
    private boolean[] ack;
    /* SR 为每个分组准备一个计时器 */
    int[] timers;
    /* 当前数据包的seq */
    private int curSeq;
    /* 当前等待确认的ack */
    private int curAck;
    /* 收到的包的总数 */
    private int totalSeq;
    /* 需要发送的总包数 */
    private int totalPacket;
    /* 已经正确被接收的包数, 返回ack则加1 */
    private int totalBeenReceived;
    /* 缓存已发送的包 */
    private byte[][] sendCacheBuff;
    /* 序列号和下标对应 */
    private HashMap<Integer, Integer> seqForCacheBuffIndexs;
    /* 可用下标 */
    private Stack<Integer> sendcacheBuffIndexs;
    private byte[] dataToClient;

    /* 套接字 */
    private DatagramSocket serverSocket;
    /* 接收数据报 */
    private DatagramPacket receivePacket;
    /* 发送数据报 */
    private DatagramPacket sendPacket;

    /* 期望收到的数据包序列号 */
    private int waitSeq;
    /* 保存请求的文件, 需要在接收状态码99时初始化 */
    private byte[] dataFromClient;
    /* 已经收到的数据的总大小, 用以保存时进行偏移 */
    private int alreadyReceiveDataLength;

    /* 缓存收到的数据报 */
    /* 缓存收到的字节数据、最大缓存为窗口大小 */
    private byte[][] receiveCacheBuff;
    /* 序列号和缓存中的对应下标 */
    HashMap<Integer, Integer> seqMapIndexInCache;
    /* 剩余的缓存下标 */
    Stack<Integer> receivecacheBuffIndexs;



    /**
     * 进行初始化操作
     */
    public ServerFull() throws SocketException {
        /* 初始化服务器套接字 */
        this.serverSocket = new DatagramSocket(this.SERVER_PORT);
        /* 初始化接收、发送缓存 */
        this.receiveBuff = new byte[this.BUFFER_LENGTH];
        this.sendBuff = new byte[this.BUFFER_LENGTH];
    }

    /**
     * 接收客户请求并做响应
     * -time 客户端请求获取当前时间, 服务器回复当前时间
     * -quit 客户端退出, 服务器回复"Good bye!"
     * -testgbn 客户端请求开始测试GBN协议, 服务器开始进入GBN传输状态
     */
    public void run() throws IOException {
        System.out.println("服务器进程已启动...");
        System.out.println("监听端口为: " + this.SERVER_PORT);
        /**
         * 监听
         * 分为两种情况
         * 1、客户的请求 -time, -quit, -testgbn
         * 2、testgbn阶段
         */
        while (true) {
            /* 为接收数据报装载接收缓存 */
            this.receivePacket = new DatagramPacket(this.receiveBuff, this.BUFFER_LENGTH);
            /* 接收数据 - 阻塞接收模式, 一直等待, 直到接收到客户请求 */
            this.serverSocket.setSoTimeout(0);
            this.serverSocket.receive(this.receivePacket);

            /* 获得客户IP地址和端口号 */
            this.CLIENT_IP = this.receivePacket.getAddress();
            this.CLIENT_PORT = this.receivePacket.getPort();

            /* 客户端请求 */
            String request = new String(
              Arrays.copyOf(this.receiveBuff, this.receivePacket.getLength()));
            /* 客户请求服务器系统时间 */
            if (request.equals("-time")) {
                /* 获得服务器系统时间 */
                String date = this.getDate();
                System.out.println("客户端请求系统当前时间...");
                this.sendPacket = new DatagramPacket(date.getBytes(), date.getBytes().length,
                  this.CLIENT_IP, this.CLIENT_PORT);
                this.serverSocket.send(this.sendPacket);

                /* 请求结束通信 */
            } else if (request.equals("-quit")) {
                System.out.println("客户端请求结束通信...");
                /* 回复信息 */
                String response = "Good bye!";
                this.sendPacket = new DatagramPacket(response.getBytes(),
                  response.getBytes().length, this.CLIENT_IP, this.CLIENT_PORT);
                this.serverSocket.send(this.sendPacket);

                /* 请求进入GBN传输状态 */
            } else if (request.equals("-testsr")) {
                System.out.println("客户端请求进入SR传输状态...");
                this.testGBN(false);

            } else if (request.equals("-testfsr")) {
                System.out.println("客户端请求进入双向SR传输状态...");
                this.testGBN(true);

            } else {
                /* 不做响应 */
            }
        }
    }

    /**
     * 获取当前系统时间
     *
     * @return yyyy/MM/dd HH:mm:ss
     */
    public String getDate() {
        /* 获取时间对象 */
        Date now = new Date();
        /* 格式化时间模板 */
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        /* 获取指定格式时间字符串 */
        String date = dateFormat.format(now);
        return date;
    }

    /**
     * 进入GBN传输状态
     * 分为GBN测试阶段和传输阶段
     * 测试阶段
     * -stage=1 发送99大小的状态码, 表示服务器准备好, 可以发送数据
     * -state=2 等待接收客户回复 100状态码, 没有收到则timer+1, 超时则退出GBN状态
     *
     * 传输阶段
     * -state=3 数据传输
     * 序列号从1开始发送并判断是否结束文件传输
     * 等待Ack, 没有收到则返回-1, 计时器加1, 20次等待ack则超时重传
     * 收到Ack
     *
     * ***************************
     * GBN传输过程中
     * 所有的接收都需要采用非阻塞接收, 即没有接收到则返回-1
     * 这样创建一个伪连接状态
     * 使用socket.setSoTimeOut设置等待时间
     * ***************************
     *
     * @param IPAddress 客户端IP地址
     * @param port 客户端端口
     */
    public void testGBN(boolean isFullDuplex) throws IOException {
        /* GBN传输状态标识 */
        boolean runFlagR = isFullDuplex;
        boolean runFlagS = true;

        /* GBN传输阶段 */
        int stage = 0;
        /* 计时器, 超过20则重传 */
        // 计时状态码的超市次数
        int timer = 0;
        /* socket超时设置, 超时则抛出异常 */
        this.serverSocket.setSoTimeout(TIMEOUT);

        /* 接受数据报 */
        this.receivePacket = new DatagramPacket(this.receiveBuff, this.BUFFER_LENGTH);

        /* 读取请求文件 */
        // TODO 可由客户指定请求文件
        this.readData("Files/server.png");

        /* 请求文件不存在, 退出循环 */
        if (this.dataToClient == null) {
            System.out.println("请求文件不存在...");
            return;
        }

        /* 进入GBN传输状态 */
        while (runFlagS || runFlagR) {
            switch (stage) {
                /* 需要进行握手确认, 发送99状态码 */
                case 0: {
                    System.out.println("服务器与客户端开始进行握手确认...");
                    System.out.println("服务器发送状态码99...");
                    /* 封装状态码 */
                    this.sendBuff[0] = 99;

                    /* 向客户端发送请求的文件大小 */
                    int bits = this.dataToClient.length / 100;
                    /* 每100位保存在99状态码的后面 */
                    for (int i = 1; i <= bits; i++) {
                        this.sendBuff[i] = (byte) 100;
                    }
                    this.sendBuff[bits + 1] = (byte) (this.dataToClient.length - bits * 100);
                    this.sendBuff[bits + 2] = '\0';

                    /* 状态码自己打包 */
                    this.sendPacket = new DatagramPacket(this.sendBuff, this.BUFFER_LENGTH,
                      this.CLIENT_IP, this.CLIENT_PORT);
                    this.serverSocket.send(sendPacket);
                    /* 等待用户回复 */
                    stage = 1;

                }
                /* 等待客户回复100状态码 */
                case 1: {
                    System.out.println("等待客户端回复状态码100...");

                    /* try-catch结构, 防止因超时则引发异常 */
                    try {

                        /* 等待接收 */
                        this.serverSocket.receive(this.receivePacket);

                        if (this.receiveBuff[0] == 100) {
                            System.out.println("收到客户端回复状态码100...");
                            System.out.println("服务器进入SR传输状态...");

                            /* 初始收到的ack以及发送的序列号等 */
                            this.curAck = 1;
                            this.curSeq = 1;
                            /* 当前已经被发送的总报文数 */
                            this.totalSeq = 0;
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

                            if (isFullDuplex) {
                                /* 解析文件大小 */
                                int fileSize = 0;
                                int index = 1;
                                while (this.receiveBuff[index] != '\0') {
                                    fileSize += this.receiveBuff[index];
                                    index++;
                                }
                                System.out.println("即将准备接收数据, 共" + fileSize + "B...");

                                /* 保存接收的文件 */
                                this.dataFromClient = new byte[fileSize];
                                /* 初始化收到的数据大小 */
                                this.alreadyReceiveDataLength = 0;
                                /* 初始化等待序列号 */
                                this.waitSeq = 41;

                                /* 初始化缓存 */
                                this.receiveCacheBuff = new byte[this.SEND_WIND_SIZE][];
                                /* 初始化序列号和缓存对应关系 */
                                this.seqMapIndexInCache = new HashMap<>();
                                /* 初始化可用缓存下标 */
                                this.receivecacheBuffIndexs = new Stack<>();
                                for (int i = 0; i < this.SEND_WIND_SIZE; i++) {
                                    this.receivecacheBuffIndexs.push(i);
                                }

                                System.out.println("接收总大小为" + this.dataToClient.length + "B...");
                            }

                            stage = 2;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        timer += 1;
                        /* 等待回复超时 */
                        if (timer > 20) {
                            System.out.println("等待客户端回复状态码100超时...");
                            /* 退出循环 */
                            runFlagS = false;
                            runFlagR = false;
                        }
                    }
                    break;
                }
                /**
                 * GBN传输状态, 开始传输数据
                 * 判断序列号是否可用、装载数据报并发送
                 *
                 * 接收ack 超时重传判断
                 *
                 **/
                case 2: {  /* case 2 begin */

                    /**
                     * curSeq 可用
                     * 则发送序列号为curSeq的报文
                     * 装载数据为 totalSeq*1024 - (totalSeq + 1)*1024
                     * 需要判断 totalSeq 是否小于 totalPacket
                     */
                    if (this.seqIsAvailable()) {
                        /* 对sendBuff进行打包, 并装载进sendPacket */
                        this.makePacket();

                        System.out.println("发送数据报: " + this.curSeq);
                        /* 发送数据报后需要做相关处理 */
                        /* 设置发送的序列号的确认状态 */
                        this.ack[this.curSeq - 1] = false;
                        /* 设置该序列号的超时设置 */
                        this.timers[this.curSeq - 1] = 0;

                        /* 当前序列号加1 */
                        this.curSeq++;
                        /* 防止序列号超过总序列号 */
                        if (this.curSeq == 21) {
                            this.curSeq = 1;
                        }
                        //this.curSeq %= this.SEQ_SIZE;
                        /* 当前已发送数据包总序列号加1 */
                        this.totalSeq++;

                        /* 发送数据 */
                        this.serverSocket.send(this.sendPacket);

                        try {
                            Thread.sleep(this.TIMEOUT);
                        } catch (Exception e) {

                        }
                    }

                    /* 接收ack */
                    /* try-catch结构, 防止超时引发异常 */
                    try {
                        this.serverSocket.receive(this.receivePacket);
                        /* 获取收到的序列号 */
                        int seq = this.receiveBuff[0];
                        /* 收到的数据包大小 */
                        int length = this.receivePacket.getLength();

                        /* 判断是否已经成功发送所有的数据报被接收 */
                        if (seq == 101) {
                            System.out.println("已经成功发送所有的数据报");
                            runFlagS = false;
                        }

                        /* 处理收到的数据 */
                        if (seq >= 40 && seq <= 61) {
                            /* 是期待的序列号, 检查缓存, 进行累计确认 */
                            if (this.waitSeq == seq) {

                                //TODO 不能直接用\0判断, 传输的数据中可能使用了\0
                                /* 提取中间的数据部分保存到data中 */
                                for (int i = 1; i < this.receivePacket.getLength() - 1; i++) {
                                    this.dataFromClient[i + this.alreadyReceiveDataLength
                                      - 1] = this.receiveBuff[i];
                                }
                                /* 更新收到的数据总大小 */
                                this.alreadyReceiveDataLength =
                                  this.alreadyReceiveDataLength + (this.receivePacket.getLength()
                                    - 2);

                                /* 启动累积确认机制, 检查缓存并发送ack, 以及更新期待的seq */
                                this.accumulateACK();

                                /* 文件总大小判断 */
                                if (this.alreadyReceiveDataLength == this.dataFromClient.length) {
                                    System.out
                                      .println(
                                        "请求数据已接受完毕, 共" + this.alreadyReceiveDataLength + "B...");
                                    Files.write(Paths.get("Files/ServerFromClient.png"),
                                      this.dataFromClient);
                                    System.out.println("保存在Files/ServerFromClient.png");
                                    runFlagR = false;

                                    /* TODO ack丢失, 但所有数据都已经收到,客户端关闭,但发送方会一直发送没有收到ack的分组, 因此需要回复101表示结束通信 */
                                    this.sendBuff[0] = 102;
                                    this.sendBuff[1] = '\0';
                                    this.sendToClient(this.sendBuff, 2);
                                }
                                /* 不是期待的序列号 - 提前到达、缓存中仍有空闲则保存, 否则直接丢弃 */
                            } else {
                                /* 计算收到序列号和当前期待序列号的间隔 */
                                int step = this.receiveBuff[0] - this.waitSeq;
                                /* 比期待的序列号大, 且缓冲中仍有空闲, 保存并回复ack */
                                // TODO 条件重复？？？
                                if (((step > 0 && step < 10) || (step < 0 && step > 10))
                                  && this.seqMapIndexInCache.size() <= this.SEND_WIND_SIZE) {
                                    /* 找看空闲的缓存下标 */
                                    int index = this.sendcacheBuffIndexs.pop();
                                    /* 保存序列号和对应缓存中的下标 */
                                    this.seqMapIndexInCache.put(seq, index);
                                    /* 保存缓存 */
                                    /* 不能直接等于receiveBuff, 会地址相等, 缓存会随之receiveBuff改变 */
                                    this.sendCacheBuff[index] = new byte[length];
                                    for (int i = 0; i < length; i++) {
                                        this.sendCacheBuff[index][i] = this.receiveBuff[i];
                                    }
                                    System.out.println("分组" + this.receiveBuff[0] + "提前到达, 放入缓存");
                                    /* 回复收到的ack, 但期待的ack并不改变 */
                                    this.sendBuff[0] = this.receiveBuff[0];
                                    this.sendBuff[1] = '\0';
                                    this.sendToClient(this.sendBuff, 2);
                                    /* 不做相关更新 */


                                    /* 比期待的序列号小或缓存中没有空闲直接丢弃 */
                                } else if ((step < 0 && -step < 10) || (step > 0 && step > 10)) {
                                    System.out.println("收到之前已经收到的分组" + this.receiveBuff[0]);
                                    this.sendBuff[0] = this.receiveBuff[0];
                                    this.sendBuff[1] = '0';
                                    this.sendToClient(this.sendBuff, 2);
                                } else {
                                    System.out.println("丢弃分组" + this.receiveBuff[0]);
                                }

                                /* 处理收到的ack */
                            }
                        } else if (this.receiveBuff[0] != 101 && this.receiveBuff[0] != 102) {
                            /* 收到ack */
                            //TODO SR超时处理

                            this.ackHandler(this.receiveBuff[0]);

                            /* 判断是否已经成功发送所有的数据报被接收 */
                            if (this.totalPacket == this.totalBeenReceived) {
                                System.out.println("已经成功发送所有的数据报");
                                this.sendBuff[0] = 101;
                                this.sendBuff[1] = '\0';
                                this.sendToClient(this.sendBuff, 2);
                                runFlagS = false;
                            }
                        }

                    } catch (IOException e) {
                        System.out.println("请求超时...");
                        /* 没有收到ack */
                        /* 处理每个 */
                        /* 更新接收情况 */
                        this.notReceive();
                    } finally {

                    }
                    //System.out.println(runFlagS+" "+runFlagR+" "+(runFlagR || runFlagS));
                    break;
                } /* case 2 end */
            } /* switch end */
        } /* while end */
        System.out.println("SR传输状态结束...");
    }

    /**
     * 读取客户请求的文件, 保存到字节数组中, 分组传输给客户
     *
     * @param filePath 请求文件地址
     * @return byte[] dataToClient, 若请求文件不存在, 则返回null
     */
    public void readData(String filePath) {
        try {
            this.dataToClient = Files.readAllBytes(Paths.get(filePath));
            /* 传递该文件所需要的总报文数, 每个报文最大传输数据1024字节, 这里需要向上取整 */
            this.totalPacket = (int) Math
              .ceil((double) dataToClient.length / (this.BUFFER_LENGTH - 2));

        } catch (IOException e) {
            /* IO异常处理 */
            this.dataToClient = null;
        }

    }

    /**
     * 判断当前序列号curSeq是否可用
     * 主要判断发送未确认数是否小于发送窗口
     *
     * ||--------------------------SEQ_SIZE=20------------------------------||
     * ||---已经确认---||-----发送未确认------||---可用,未发送---||---不可用---||
     * ||-------------||----------SEND_WIN_SIZE=10------------||------ -----||
     * ||-------------||curAck--------------||curSeq
     *
     * @return true if available, otherwise false
     */
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
        if (step >= this.SEND_WIND_SIZE) {
            return false;
        }

        /* 已经被接收了, 可以使用该序列号, TODO 多此一举？？？ */
        if (this.ack[this.curSeq - 1]) {
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
            length = this.dataToClient.length - this.totalSeq * (this.BUFFER_LENGTH - 2);
        }
        /* 首位填入当前序列号, 序列号从1开始 */
        this.sendBuff[0] = (byte) this.curSeq;

        /* 装载数据 */
        for (int i = 1; i <= length; i++) {
            this.sendBuff[i] = this.dataToClient[offset + i - 1];
        }
        /* 设置最后一位为0 */
        // TODO 最后一位设置EOF？？？
        this.sendBuff[length + 1] = '\0';
        /* 将sendBuff装载到sendPacket中, 长度为length+2 */
        this.sendPacket = new DatagramPacket(this.sendBuff, length + 2, this.CLIENT_IP,
          this.CLIENT_PORT);

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
        this.sendToClient(this.sendBuff, this.BUFFER_LENGTH);

        /* 检查缓存中已接收紧接着期待ack的序列号 */
        int existSeq = 0;
        for (int i = 1; i <= this.SEND_WIND_SIZE; i++) {
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
                if (this.waitSeq == 61) {
                    this.waitSeq = 41;
                }
                /* 序列号在缓存中对于的下标 */
                int index = this.seqMapIndexInCache.get(this.waitSeq);
                /* 读取 */
                for (int j = 0; j < this.receiveCacheBuff[index].length - 2; j++) {
                    this.dataFromClient[this.alreadyReceiveDataLength
                      + j] = this.receiveCacheBuff[index][j
                      + 1];
                }
                /* 更新可用缓存下标 */
                this.receivecacheBuffIndexs.push(index);
                /* 删除缓存下标和序列号对于关系, 即使不删除, 后面添加时很也会更新 */
                this.seqMapIndexInCache.remove(this.waitSeq);
                /* 更新收到数据的长度 */
                this.alreadyReceiveDataLength =
                  this.alreadyReceiveDataLength + this.receiveCacheBuff[index].length - 2;

            }
        }

        /* 下一个期待的序列号 */
        this.waitSeq++;
        if (this.waitSeq == 61) {
            this.waitSeq = 41;
        }

    }

    /**
     * 处理收到的ack, 累计确认
     *
     * 两种情况（是否超过最大值）
     * ack > curAck
     * ack < curAck
     *
     * @param c 数据帧第一个字节
     */
    public void ackHandler(byte receiveAck) {

        /* SR只对收到的ack进行确定 */
        System.out.println("收到ack: " + receiveAck);
        /* 标识已经收到 */
        this.ack[receiveAck - 1] = true;

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
                if (this.ack[i - 1]) {
                    this.curAck++;
                } else {
                    break;
                }
            }
        } else {
            for (int i = this.curAck; i <= 20; i++) {
                if (this.ack[i - 1]) {
                    this.curAck++;
                } else {
                    break;
                }
            }
            if (this.curAck == 21) {
                this.curAck = 1;
                for (int i = 1; i < this.curSeq; i++) {
                    if (this.ack[i - 1]) {
                        this.curAck++;
                    } else {
                        break;
                    }
                }
            }


        }
    }

    /**
     * 没有收到ack处理
     * 将没有收到ack的计时器加1
     * 如果该ack超时了则重发该ack
     */
    public void notReceive() throws IOException {
        /* 没有收到ack, 为每个发送还没确认的seq计时器加1 */
        if (this.curSeq >= this.curAck) {
            for (int i = this.curAck - 1; i < this.curSeq; i++) {
                /* 没有收到 */
                if (!this.ack[i]) {
                    this.timers[i]++;
                    /* 超时重传 */
                    if (this.timers[i] > 20) {
                        this.timeoutHandler(i + 1);
                    }
                }
            }
        } else {
            /* 窗口不在正中间 */
            for (int i = this.curAck - 1; i < 20; i++) {
                /* 没有收到 */
                if (!this.ack[i]) {
                    this.timers[i]++;
                    /* 超时处理 */
                    if (this.timers[i] > 20) {
                        this.timeoutHandler(i + 1);
                    }
                }
            }
            for (int i = 0; i < this.curSeq; i++) {
                /* 没有收到 */
                if (!this.ack[i]) {
                    this.timers[i]++;
                    /* 超时处理 */
                    if (this.timers[i] > 20) {
                        this.timeoutHandler(i + 1);
                    }
                }
            }
        }


    }

    /**
     * GBN超时重传处理函数, 滑动窗口内的数据帧都要重传
     * SR重传超时的序列号
     */
    public void timeoutHandler(int seq) throws IOException {

        //SR 超时选择重传超时的序列号
        System.out.println("ack" + seq + "超时, 准备重传");
        this.ack[seq - 1] = false;
        this.timers[seq - 1] = 0;
        int cacheIndex = this.seqForCacheBuffIndexs.get(seq);
        /* 发送该超时的seq */
        this.sendPacket = new DatagramPacket(this.sendCacheBuff[cacheIndex],
          this.sendCacheBuff[cacheIndex].length, this.CLIENT_IP, this.CLIENT_PORT);
        this.serverSocket.send(this.sendPacket);


    }

    public boolean sendToClient(byte[] pktData, int length) throws IOException {

        /* 封装数据包 */
        this.sendPacket = new DatagramPacket(pktData, length, this.CLIENT_IP, this.CLIENT_PORT);
        /* 发送 */
        this.serverSocket.send(this.sendPacket);
        System.out.println("成功发送ack: " + pktData[0]);

        return true;
    }


    public static void main(String[] args) throws IOException {
        ServerFull server = new ServerFull();
        server.run();
    }

}
