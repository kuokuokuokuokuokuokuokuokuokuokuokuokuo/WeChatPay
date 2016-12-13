package atyy.wechatpay;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by liu kuo on 2016/12/8 0008.
 */
public class QRfromGoogle {
    public static String QRfromGoogle(String chl) throws Exception {
        int widhtHeight = 300;
        String EC_level = "L";
        int margin = 0;
        chl = UrlEncode(chl);
        String QRfromGoogle = "http://chart.apis.google.com/chart?chs=" + widhtHeight + "x" + widhtHeight
                + "&cht=qr&chld=" + EC_level + "|" + margin + "&chl=" + chl;

        return QRfromGoogle;
    }
    public static String UrlEncode(String src)  throws UnsupportedEncodingException {
        return URLEncoder.encode(src, "UTF-8").replace("+", "%20");
    }
}

