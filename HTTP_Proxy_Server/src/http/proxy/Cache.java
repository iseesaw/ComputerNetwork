package http.proxy;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 缓存管理 1、获取客户的请求头
 * 2、判断请求URL是否有缓存
 * 3、若有缓存, 则发送if-modified, 请求或直接响应给客户主机
 * 4、若无缓存, 直接请求, 并将请求结果缓存
 */
public class Cache {

    /**
     * 判断缓存中是否存在
     *
     * @param url 请求文件地址
     * @return 存在则返回true, 返回返回false
     */
    public static boolean isCached(String url) {
        /* 获得Cache文件夹下所有文件 */
        File cacheAddress = new File("Cache");
        File[] cacheFiles = cacheAddress.listFiles();
        /* 判断缓存中是否存在url的缓存 */
        for (File file : cacheFiles) {
            /* 将url编码, 如果缓存中存在, 则返回true */
            if (file.getName().equals(getFileName(url))) {
                return true;
            }
        }
        /* 不存在, 返回false */
        return false;
    }

    /**
     * 得到缓存的最后更改时间
     *
     * @return 最后修改时间
     */
    public static String getModifiedSince(String url) throws IOException {
        /* 获取请求文件编码 */
        String filePath = "Cache/" + getFileName(url);
        /* 从缓存中读取文件 */
        byte[] cacheBytes = Files.readAllBytes(Paths.get(filePath));
        /* 需要指定字符集, 否则byte和str转换会丢失 */
        String cacheStr = new String(cacheBytes, "ISO-8859-1");
        /* 分割得到每行 */
        String[] cacheLines = cacheStr.split("\r\n");
        /* 遍历获得时间 */
        for (String cacheLine : cacheLines) {
            /* 分割得到时间并返回 */
            if (cacheLine.startsWith("Last-Modified")) {
                return cacheLine;
            }
        }
        /* 查询失败 , 返回空*/
        return null;
    }

    /**
     * 编码请求文件地址为标准文件名
     *
     * @param distributionUrl 请求文件名
     * @return 编码后文件名
     */
    public static String getFileName(String distributionUrl) {
        MessageDigest messageDigest = null;
        try {
            /* 对url进行MD5编码 */
            messageDigest = MessageDigest.getInstance("MD5");
            /* 根据MD5构造uint128位整数 */
            messageDigest.update(distributionUrl.getBytes());
            /* 将128为整数使用base32编码 */
            String str = new BigInteger(1, messageDigest.digest()).toString(36);
            return str;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 从缓存文件中取出
     *
     * @param url 请求文件地址
     * @return 缓存字节
     */
    public static byte[] getCache(String url) throws IOException {
        String filePath = "Cache/" + getFileName(url);
        /* 从缓存文件中读取 */
        byte[] cacheBytes = Files.readAllBytes(Paths.get(filePath));
        /* 写入文件以\r\n分割, 读取出来仍然是\r\n */
        /* 返回缓存字节 */
        return cacheBytes;
    }

    /**
     * 如果缓存里没有, 将响应写入缓存
     *
     * @param respones 响应
     * @param url 请求文件地址
     */
    public static void addCache(Respones respones, String url) {
        /* 获得编码后的文件名并写入缓存文件夹 */
        String filePath = "Cache/" + getFileName(url);
        try {
            Files.write(Paths.get(filePath), respones.getResponse());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
