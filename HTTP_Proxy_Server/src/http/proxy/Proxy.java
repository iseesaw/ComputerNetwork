package http.proxy;

import java.io.*;
import java.net.*;
import java.nio.file.attribute.FileTime;

/**
 * 代理服务器线程
 */

public class Proxy implements Runnable {

    /* 代理套接字, 连接代理服务器和客户主机 */
    private Socket proxyToClient;
    /* 连接代理服务器和目的服务器 */
    private Socket proxyToServer;
    /* 客户的请求头 */
    private Headers headers;
    /* 目的服务器的响应报文 */
    private Respones respones;
    /* 是否缓存 */
    private boolean isCached;

    public Proxy(Socket proxy) {
        this.proxyToClient = proxy;
        this.isCached = false;
    }

    /**
     * 实现代理的全部功能
     * 接收客户的请求报文并解析 Headers()
     * 转发客户的请求报文给目的服务器
     * 接收目的服务器的响应报文 Respones()
     * 转发目的服务器的响应报文给客户
     */
    @Override
    public void run() {
        /* 转发给客户的输出流 */
        DataOutputStream outputToClient = null;
        try {
            outputToClient = new DataOutputStream(
              this.proxyToClient.getOutputStream());
        } catch (IOException e) {
            return;
        }

        /* 获取客户的请求头 */
        this.headers = new Headers();
        try {
            this.headers.recieve(this.proxyToClient.getInputStream());
            /* 判断请求方式, 只支持GET和POST */
            if (!this.headers.getMethod().equals("GET") && !this.headers.getMethod()
              .equals("POST")) {
                System.out.println("仅支持HTTP连接 " + this.headers.getURL());
                return;
            }
        } catch (Exception e) {
            /* 请求头为空或不是GET或POST */
            System.out.println("解析请求报文失败 " + this.headers.getURL());
            return;
        }
        System.out.println(this.headers.getMethod() + " " + this.headers.getURL());

        /* 过滤用户、网站、重定向 */
        /* 过滤网站 */
        if (Filter.isDisallowedWebsites(this.headers.getURL())) {
            try {
                System.err.println("禁止访问网站 " + this.headers.getURL());
                this.proxyToClient.close();
                return;
            } catch (IOException e) {
                /* 未过滤, 则访问原网站 */
            }

        }
        String forbiden;
        /* 过滤用户 */
        if ((forbiden = Filter.isDisalloedUsers(this.proxyToClient.getInetAddress().getHostAddress())) !=null)
        {
            try {
                System.err
                  .println(
                    "禁止用户 " + this.proxyToClient.getInetAddress().getHostAddress() + " 访问外部网络");
                outputToClient.writeBytes(forbiden);
                outputToClient.flush();
                this.proxyToClient.close();
                return;
            } catch (IOException e) {
                /* 未过滤, 则访问原网站 */
            }
        }
        /* 引导 */
        String phishingWebsite = Filter.leadToPhishingWebsite(this.headers.getURL());
        if (phishingWebsite != null) {
            try {
                outputToClient.writeBytes(phishingWebsite);
                outputToClient.flush();
                this.proxyToClient.close();
                System.err
                  .println("对 " + this.headers.getURL() + "的访问引导至 " + "http://www.hit.edu.cn");
                return;
            } catch (IOException e) {
                /* 引导失败, 访问原网站 */
            }
        }
        /**
         * 检查是否存在缓存
         *   如果存在, 获取缓存时间, 并且插入请求头转发给目的服务器, 检查是否最新
         *         如果是最新, 提取缓存发送客户
         *         否则, 接收响应, 缓存并发送给客户
         *   否则, 转发请求, 接收响应, 缓存并发送给客户
         *   If-Modified-Since只用于GET或HEAD
         */

        try {
            /* 创建代理服务器到目的服务器的套接字 */
            this.proxyToServer = new Socket(this.headers.getHost(), this.headers.getPort());
            /* 代理服务器转发客户的请求头 */
            DataOutputStream outputToServer = new DataOutputStream(
              this.proxyToServer.getOutputStream());
            String modifiedSince;
            /* 已经缓存并且Date不为null */
            if (this.headers.getMethod().equals("GET") && Cache.isCached(this.headers.getURL())
              && (modifiedSince =
              Cache.getModifiedSince(this.headers.getURL())) != null) {
                /* 更新是否缓存 */
                this.isCached = true;
                /* 将最后更新时间插入头部 */
                this.headers.insertIfModifiedSince(modifiedSince);
            }
            /* 转发给目的服务器 */
            outputToServer.writeBytes(this.headers.getHeaders());
        } catch (IOException e) {
            System.out.println("连接目的服务器失败" + this.headers.getURL());
            return;
        }

        /* 接收目的主机的响应 */
        this.respones = new Respones();
        try {
            /* 接收目的服务器的输入流 */
            this.respones.recieve(this.proxyToServer.getInputStream());

            if (this.headers.getMethod().equals("GET")) {
                /* 如果缓存了, 则判断是否最新 */
                if (this.isCached) {
                    /* 缓存是最新的, 读取缓存更新响应 */
                    if (this.respones.getStateCode() == 304) {
                        System.out.println("缓存已最新 " + this.headers.getURL());
                        this.respones.replaceByCache(this.headers.getURL());
                    } else {
                        System.out.println("缓存需要更新 " + this.headers.getURL());
                        /* 缓存不是最新的, 更新缓存 */
                        Cache.addCache(this.respones, this.headers.getURL());
                    }
                } else {
                    System.out.println("未缓存 " + this.headers.getURL());
                    /* 如果没有缓存则缓存 */
                    Cache.addCache(this.respones, this.headers.getURL());
                }
            }

            /* 转发目的服务器的响应 */
            outputToClient.write(this.respones.getResponse());
            outputToClient.flush();
        } catch (IOException e) {
            System.out.println("接收目的主机响应出错" + this.headers.getURL());
        }
        /* 关闭套接字 */
        try {
            this.proxyToClient.close();
            this.proxyToServer.close();

        } catch (IOException e) {
        }
    }

}
