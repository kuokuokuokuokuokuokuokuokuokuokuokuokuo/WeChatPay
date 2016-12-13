package atyy.web.pay;



import atyy.common.ConstantVar;
import atyy.common.WeChatConfig;
import atyy.service.IFileService;
import atyy.spring.log4j.anonation.Logger;
import atyy.wechatpay.HttpUtil;
import atyy.wechatpay.PayCommonUtil;
import atyy.wechatpay.XMLUtil;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import org.apache.commons.logging.Log;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import javax.imageio.ImageIO;
import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 *  支付的 action ,包括了weChat 和 aliPay (从发起到回调)
 *  Create by liu kuo 2016/12/9.
*/
@Controller
@RequestMapping("pay")
public class PayAction {

    /** 日志 **/
    @Logger
    private Log log;

    /** 文件上传记录表服务 */
    @Autowired
    @Qualifier("fileService")
    private IFileService fileService;

    //返回状态SUCCESS/FAIL
    private static String SUCCESS = "SUCCESS";
    private static String FAIL = "FAIL";

    /**
     * 微信统一预下单,统一链接,返回二维码
     */
    @RequestMapping(value = "WxPayUnifiedOrder", method = RequestMethod.GET)
    @ResponseBody
    public void WxUnifiedOrder(HttpServletResponse response) throws Exception
    {
        String appid = WeChatConfig.APP_ID;  // app_id
        String mch_id = WeChatConfig.MCH_ID; // 商业号
        String key = WeChatConfig.API_KEY; // api_key
        //创建一个32位以内的随机字符串
        String currTime = PayCommonUtil.getCurrTime();
        String strTime = currTime.substring(8, currTime.length());
        String strRandom = PayCommonUtil.buildRandom(4) + "";
        String nonce_str = strTime + strRandom;
        // todo 订单信息 , 暂时虚拟 , 以后添加 具体信息
        String order_price = "1"; // 价格   注意：价格的单位是分
        String body = "打气筒";   // 商品名称
        String out_trade_no = "18801404976_test_4"; // 订单号

        // 获取发起电脑 ip , 其实是没什么用的
        String spbill_create_ip = WeChatConfig.CREATE_IP;
        // 回调接口,微信返回支付状态的回调接口
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
        //生成签名
        String sign = PayCommonUtil.createSign("UTF-8", packageParams, key);
        packageParams.put("sign", sign);
        //把信息生成为XML格式
        String requestXML = PayCommonUtil.getRequestXml(packageParams);
        //预下单
        String resXml = HttpUtil.postData(WeChatConfig.UFDODER_URL, requestXML);
        Map map = XMLUtil.doXMLParse(resXml);
        String return_code = (String) map.get("return_code");
        String prepay_id = (String) map.get("prepay_id");
        String urlCode = (String) map.get("code_url");
        log.info("*return_code" + return_code + "*prepay_id" + prepay_id + "urlCode" + urlCode);

        //生成二维码,返回到页面-------------
        if(urlCode==null || "".equals(urlCode)){
            return;
        }
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        Map hints = new HashMap();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8"); //设置字符集编码类型
        BitMatrix bitMatrix = null;
        try {
            bitMatrix = multiFormatWriter.encode(urlCode, BarcodeFormat.QR_CODE, 300, 300,hints);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            BufferedImage image = new BufferedImage(width, height,BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    image.setRGB(x, y, bitMatrix.get(x, y) == true ?
                            Color.BLACK.getRGB():Color.WHITE.getRGB());
                }
            }
            //输出二维码图片流
            try {
                ImageIO.write(image, "png", response.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (WriterException e1) {
            e1.printStackTrace();
        }
    }

    /**
     * 订单退款的,链接需要,订单中我们的订单号,重复提交只会有一次退款,ORIGINAL—原路退回;BALANCE—退回到余额

        返回的xml
     *  <xml>
         <return_code><![CDATA[SUCCESS]]></return_code>
         <return_msg><![CDATA[OK]]></return_msg>
         <appid><![CDATA[wx2421b1c4370ec43b]]></appid>
         <mch_id><![CDATA[10000100]]></mch_id>
         <device_info><![CDATA[1000]]></device_info>
         <nonce_str><![CDATA[TN55wO9Pba5yENl8]]></nonce_str>
         <sign><![CDATA[BDF0099C15FF7BC6B1585FBB110AB635]]></sign>
         <result_code><![CDATA[SUCCESS]]></result_code>
         <openid><![CDATA[oUpF8uN95-Ptaags6E_roPHg7AG0]]></openid>
         <is_subscribe><![CDATA[Y]]></is_subscribe>
         <trade_type><![CDATA[MICROPAY]]></trade_type>
         <bank_type><![CDATA[CCB_DEBIT]]></bank_type>
         <total_fee>1</total_fee>
         <fee_type><![CDATA[CNY]]></fee_type>
         <transaction_id><![CDATA[1008450740201411110005820873]]></transaction_id>
         <out_trade_no><![CDATA[1415757673]]></out_trade_no>
         <attach><![CDATA[订单额外描述]]></attach>
         <time_end><![CDATA[20141111170043]]></time_end>
         <trade_state><![CDATA[SUCCESS]]></trade_state>
        </xml>
     * @return
     */
    @RequestMapping(value = "refund", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> orderRefund(String idOrder) throws Exception
    {
        //---------------------------------------------------------------------------------------------------
        Map<String, Object> result = new HashMap<>();
        String appid = WeChatConfig.APP_ID;  // app_id
        String mch_id = WeChatConfig.MCH_ID; // 商业号
        String key = WeChatConfig.API_KEY; // api_key
        //todo 需要证书认证,未完成
        KeyStore keyStore  = KeyStore.getInstance("PKCS12");//keytool 为 java的数据证书管理工具 keystore 为证书存放的数据,初始化,存储为PKCS12类型(type)
        FileInputStream instream = new FileInputStream(new File("C:\\Users\\Administrator\\Desktop\\cert\\apiclient_cert.p12"));//获得证书文件
        try {
            keyStore.load(instream, mch_id.toCharArray());//加载证书数据
        } finally {
            instream.close();
        }
        //JDK文档指出，SSLSocket扩展Socket并提供使用SSL或TLS协议的安全套接字。这种套接字是正常的流套接字，但是它们在基础网络传输协议(如TCP)上添加了安全保护层
        //双向认证 *** 相信自己的CA和所有自签名的证书
        SSLContext sslcontext = SSLContexts.custom().loadKeyMaterial(keyStore, mch_id.toCharArray()).build();
        // Allow TLSv1 protocol only 只允许使用TLSv1协议
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                sslcontext,
                new String[] { "TLSv1" },
                null,
                SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
        //创建连接
        CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();

        //创建一个32位以内的随机字符串
        String currTime = PayCommonUtil.getCurrTime();
        String strTime = currTime.substring(8, currTime.length());
        String strRandom = PayCommonUtil.buildRandom(4) + "";
        String nonce_str = strTime + strRandom;
        //必要的信息字段
        String out_refund_no = "110120119lk";//内部退款单号
        String total_fee = "1";//退款金额,单位分
        String refund_fee = "1";
        String out_trade_no = "18801404976_test_3";
        //构建XML文件
        SortedMap<Object,Object> packageParams = new TreeMap<>();
        packageParams.put("appid", appid);//微信分配的公众账号ID（企业号corpid即为此appId）
        packageParams.put("mch_id", mch_id);//微信支付分配的商户号
        packageParams.put("out_refund_no", out_refund_no);//商户系统内部的退款单号，商户系统内部唯一，同一退款单号多次请求只退一笔
        packageParams.put("total_fee", total_fee);//订单总金额，单位为分，只能为整数
        packageParams.put("refund_fee", refund_fee);//退款总金额，订单总金额，单位为分，只能为整数
        packageParams.put("op_user_id", mch_id);//操作人员的标识,默认mch_id
        packageParams.put("nonce_str", nonce_str);//随机串
        packageParams.put("out_trade_no", out_trade_no);//订单号
        //生成签名
        String sign = PayCommonUtil.createSign("UTF-8", packageParams, key);
        packageParams.put("sign", sign);
        //把信息生成为XML格式
        String requestXML = PayCommonUtil.getRequestXml(packageParams);
        String jsonStr;
        try {
            HttpPost httpost = new HttpPost(WeChatConfig.REFUND_URL); // 设置响应头信息
            httpost.addHeader("Connection", "keep-alive");
            httpost.addHeader("Accept", "*/*");
            httpost.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            httpost.addHeader("Host", "api.mch.weixin.qq.com");
            httpost.addHeader("X-Requested-With", "XMLHttpRequest");
            httpost.addHeader("Cache-Control", "max-age=0");
            httpost.addHeader("User-Agent", "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 6.0) ");
            httpost.setEntity(new StringEntity(requestXML, "UTF-8"));
            CloseableHttpResponse response = httpClient.execute(httpost);
            try {
                //得到返回体中的内容
                HttpEntity entity = response.getEntity();
                //json化
                jsonStr = EntityUtils.toString(response.getEntity(), "UTF-8");
                //关闭
                EntityUtils.consume(entity);
            } finally {
                response.close();
            }
        } finally {
            httpClient.close();
        }
        log.info("*****" + jsonStr);
        Map map = XMLUtil.doXMLParse(jsonStr);
        if (SUCCESS.equalsIgnoreCase((String) map.get("return_code"))){
            log.info("退款成功");
            result.put(ConstantVar.RETURN_STATUS, ConstantVar.SUCCESS);
            result.put(ConstantVar.RETURN_MESSAGE, "退款成功");
        }else{
            log.info("退款失败");
            result.put(ConstantVar.RETURN_STATUS, ConstantVar.FAILURE);
            result.put(ConstantVar.RETURN_MESSAGE, "退款失败");
        }
        return result;
    }
}
