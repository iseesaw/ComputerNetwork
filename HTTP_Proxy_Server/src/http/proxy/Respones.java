package http.proxy;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * 从目的服务器接收数据并保存
 */
public class Respones {

    /* 保存响应 */
    private byte[] response;
    private final int MAXSIZE = 100000;
    private int totalByteRead;
    /* 状态码 */
    private int stateCode;

    public Respones() {
        this.response = new byte[MAXSIZE];
        this.totalByteRead = 0;
    }

    /**
     * 接收目的服务器的响应
     *
     * @param inFromServer 来自目的服务器的输入流
     */
    public void recieve(InputStream inFromServer) throws IOException {
        /* 每次读取的字节数 */
        byte[] buff = new byte[65535];
        int byteRead = inFromServer.read(buff);
        while (byteRead != -1) {
            for (int i = 0; i < byteRead && this.totalByteRead + i < 100000; i++) {
                this.response[i + this.totalByteRead] = buff[i];
            }
            this.totalByteRead += byteRead;
            byteRead = inFromServer.read(buff);
        }

    }

    /**
     * 获取从目的服务器接收的响应
     *
     * @return 响应字节数组
     */
    public byte[] getResponse() {
        return Arrays.copyOf(this.response, this.totalByteRead);
    }

    /**
     * 获取响应状态码
     *
     * @return 响应状态码
     */
    public int getStateCode() throws UnsupportedEncodingException {
        String responseStr = new String(this.response, "ISO-8859-1");
        this.stateCode = Integer.valueOf(responseStr.split(" ")[1]);
        return this.stateCode;
    }

    /* 读取缓存, 并替换掉接收的响应码为300的响应 */
    public void replaceByCache(String url) throws IOException {
        this.response = Cache.getCache(url);
    }
}

