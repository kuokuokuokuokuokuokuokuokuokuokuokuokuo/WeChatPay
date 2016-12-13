package atyy.wechatpay;

import atyy.common.WeChatConfig;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by liu kuo on 2016/12/8 0008.
 */
public class WeiChatPayMain {

    public static void main(String[] args) throws Exception{
        // 账号信息
        String appid = WeChatConfig.APP_ID;  // appid
        //String appsecret = PayConfigUtil.APP_SECRET; // appsecret
        String mch_id = WeChatConfig.MCH_ID; // 商业号
        String key = WeChatConfig.API_KEY; // key

        String currTime = PayCommonUtil.getCurrTime();
        String strTime = currTime.substring(8, currTime.length());
        String strRandom = PayCommonUtil.buildRandom(4) + "";
        String nonce_str = strTime + strRandom;

        String order_price = "1"; // 价格   注意：价格的单位是分
        String body = "猪肉";   // 商品名称
        String out_trade_no = "18801404976lk1"; // 订单号

        // 获取发起电脑 ip
        String spbill_create_ip = WeChatConfig.CREATE_IP;
        // 回调接口
        String notify_url = WeChatConfig.NOTIFY_URL;
        //JSAPI--公众号支付、NATIVE--原生扫码支付、APP--app支付
        String trade_type = "NATIVE";

        SortedMap<Object,Object> packageParams = new TreeMap<>();
        packageParams.put("appid", appid);
        packageParams.put("mch_id", mch_id);
        packageParams.put("nonce_str", nonce_str);//随机串
        packageParams.put("body", body);
        packageParams.put("out_trade_no", out_trade_no);//订单号
        packageParams.put("total_fee", order_price);
        packageParams.put("spbill_create_ip", spbill_create_ip);//发起人的IP,没啥用
        packageParams.put("notify_url", notify_url);
        packageParams.put("trade_type", trade_type);

        String sign = PayCommonUtil.createSign("UTF-8", packageParams,key);

        packageParams.put("sign", sign);

        String requestXML = PayCommonUtil.getRequestXml(packageParams);
        System.out.println(requestXML);

        String resXml = HttpUtil.postData(WeChatConfig.UFDODER_URL, requestXML);


        Map map = XMLUtil.doXMLParse(resXml);
        String return_code = (String) map.get("return_code");
        String prepay_id = (String) map.get("prepay_id");
        String urlCode = (String) map.get("code_url");

//        return urlCode;
        System.out.println("***"+urlCode);
        System.out.println("***"+return_code);
        System.out.println("***"+prepay_id);
    }
}
