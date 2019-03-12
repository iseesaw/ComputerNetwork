package http.proxy;

/**
 * 网站过滤：允许、不允许访问某些网站
 * 用户过滤：支持、不支持某些用户访问外部网站
 * 网站引导：将用户对某个网站的访问引导至一个模拟网站（钓鱼）
 */
public class Filter {

    /**
     * http://ito.hit.edu.cn/
     * http://www.lib.hit.edu.cn/
     * http://news.hit.edu.cn/
     * http://today.hit.edu.cn/
     * http://jwts.hit.edu.cn/
     * http://www.moe.edu.cn/
     */
    /* 不允许访问的网站 */
    private static String[] disallowedWebsites = {
      "http://baby.taobao.com/"
    };
    /* 不支持访问外部网络的用户 */
    private static String[] disallowedUsers = {
      "127.0.0.1"
    };
    private static String[] phishingWebsites = {};
    /* 重定向报文 */
    private static String response302 = "HTTP/1.1 302 Moved Temporarily\r\nLocation: http://iseesaw.xyz\r\n\r\n";

    private static String redirect = "HTTP/1.1 200 OK\r\n" +
      "Server: nginx\r\n" +
      "Content-Type: text/html\r\n\r\n" +
      "hello world";
      
    private static String forbidden = "HTTP/1.1 403 Forbidden\r\n" +
      "Server: nginx\r\n";

    /**
     * 过滤网站
     */
    public static boolean isDisallowedWebsites(String url) {
        for (String disallowedSite : disallowedWebsites) {
            if (url.equals(disallowedSite)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 过滤用户
     *
     * @param user 用户
     * @return 不允许访问外网则返回true, 允许则返回false
     */
    public static String isDisalloedUsers(String user) {
        for (String disallowedUser : disallowedUsers) {
            if (disallowedUser.equals(user)) {
                return forbidden;
            }
        }
        return null;
    }

    /**
     * pku.edu.cn
     * moe.edu.cn
     * ...
     * 将所有 .edu.cn钓鱼到 hit.edu.cn
     *
     * @param url 客户请求访问的网站
     */
    public static String leadToPhishingWebsite(String url) {
        /* 访问非hit的edu网站则重定向到hit.edu.cn */
        if (url.contains("ids.hit.edu.cn")) {
            return response302;
            //return redirect;
        }
        return null;
    }

}
