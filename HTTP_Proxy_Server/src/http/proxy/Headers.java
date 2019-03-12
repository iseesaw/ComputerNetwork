package http.proxy;

import java.io.*;

/**
 * 解析并保存客户的请求头
 */
public class Headers {

    /* POST或GET */
    private String method;
    /* 请求的URL */
    private String url;
    /* 目标主机 */
    private String host;
    /* 端口默认为80 */
    private int port = 80;
    /* 保存整个头部 */
    private StringBuilder headers;

    public Headers() {
        this.headers = new StringBuilder();
    }

    /**
     * 接收并解析来自客户的请求报文
     */
    public void recieve(InputStream inFromClient) {
        BufferedReader bf = new BufferedReader(new InputStreamReader(inFromClient));
        try {
            String line = bf.readLine();
            int flag = 1;
            /* 读取报文头 */
            while (line != null && line.length() != 0) {
                /* 保存请求头 */
                headers.append(line + "\r\n");
                /* 解析请求方法 */
                if (flag == 1) {
                    this.resolveMethod(line);
                    flag = 0;
                }
                /* 解析主机和端口 */
                if (line.startsWith("Host")) {
                    this.resolveHost(line);
                }
                line = bf.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解析首部行
     *
     * @param line Method URL Version
     */
    public void resolveMethod(String line) {
        String[] temp = line.split(" ");
        this.method = temp[0];
        this.url = temp[1];
    }

    /**
     * 解析主机和端口
     *
     * @param line Host: host:port
     */
    public void resolveHost(String line) {
        String[] temp = line.split(" ");
        /* 获得 host:port */
        String[] hostAndPort = temp[1].split(":");
        host = hostAndPort[0];
        /* 解析端口, 没有则使用默认端口 */
        if (hostAndPort.length > 1) {
            port = Integer.valueOf(hostAndPort[1]);
        }
    }

    /* 插入if modified since */
    public void insertIfModifiedSince(String date) {
        String ifModifiedSince = date.replace
          ("Last-Modified", "If-Modified-Since") + "\r\n";
        this.headers.append(ifModifiedSince);
    }

    /********** begin ** 私有变量的get方法 *********/
    public String getHeaders() {
        /* Very Important, add one more\r\n at the last */
        headers.append("\r\n");
        return this.headers.toString();
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public String getMethod() {
        return this.method;
    }

    public String getURL() {
        return this.url;
    }

    /********** end *****************************/


}
