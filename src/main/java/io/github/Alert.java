package io.github;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author ：宁鑫
 * @date ：2022/1/10 23:26
 * @description：异常处理类
 */
@Slf4j
public class ExceptionHandling {
    private String lastBeanClass;
    private String typeCause;
    private String messageCause;

    /*
     *   功能描述:添加错误信息
     *   @Param:错误信息
     */
    public ExceptionHandling(Throwable throwable) {
        Map<String, Object> throwMap = throwableToMap(throwable);
        handleEsted(throwMap);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("错误类型", typeCause);
        jsonObject.put("错误原因", messageCause);
        jsonObject.put("最后一次调用位置", lastBeanClass);
        log.error("出错了：{}", jsonObject);
    }

    /*
     *   功能描述:最紧急，需要发送报警短信
     *   @Param:错误信息、错误具体位置
     */
    public ExceptionHandling(Throwable throwable, String urgentError) {
        Map<String, Object> throwMap = throwableToMap(throwable);
        handleEsted(throwMap);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("错误类型", typeCause);
        jsonObject.put("错误原因", messageCause);
        jsonObject.put("最后一次调用位置", lastBeanClass);
        sendWarnSms("紧急错误！" + urgentError + "出错了", "电话号码");
        log.error("出错了：{}", jsonObject);
    }

    /*
     *   功能描述:添加额外错误信息
     *   @Param:错误信息、额外错误信息
     */
    public ExceptionHandling(Throwable throwable,JSONObject addInformation) {
        Map<String, Object> throwMap = throwableToMap(throwable);
        handleEsted(throwMap);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("错误类型", typeCause);
        jsonObject.put("错误原因", messageCause);
        jsonObject.put("最后一次调用位置", lastBeanClass);
//        拼接补充信息
        Map<String, Object> addInformationMap = addInformation.toMap();
        Set<Map.Entry<String, Object>> entries = addInformationMap.entrySet();
        for (Map.Entry<String, Object> next : entries) {
            jsonObject.put(next.getKey(), next.getValue());
        }
        log.error("出错了：{}", jsonObject);
    }

    /*
     *   功能描述:发送警报短信
     *   @Param:警报内容、电话号码
     */
    public void sendWarnSms(String errorMsg, String phoneNumbers) {
        HashMap<String, Object> params = new HashMap<>();
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        String format = dateFormat.format(date);

        params.put("name", "监控");
        params.put("errorMsg", errorMsg + "时间为：" + format);
        params.put("level", 1);
        params.put("phoneNumbers", phoneNumbers);
        params.put("templateCode", "SMS_163055819");

        try {
            String result = get("发送短信的url",
                    params);
            log.info("定时查询订单数发送短信成功，结果：{}", result);
        } catch (Exception e) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("定时查询订单数发送短信失败，时间", format);
            new ExceptionHandling(e, jsonObject);
            e.printStackTrace();
        }
    }

    /*
     *   功能描述: 发送httpget请求
     */
    private String get(String url, Map<String, Object> map) throws Exception {
        URIBuilder builder = new URIBuilder(url);
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                builder.setParameter(entry.getKey(), entry.getValue().toString());
            }
        }
        URI uri = builder.build();
        HttpGet httpGet = new HttpGet(uri);
        httpGet.setHeader("Accept", "application/json");
        CloseableHttpResponse response = HttpClientBuilder.create().build().execute(httpGet);
        return commonResult(response);
    }

    /*
     *   功能描述: 处理请求返回信息
     */
    private String commonResult(CloseableHttpResponse response) throws Exception {
        try {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200 && response.getEntity() != null) {
                return EntityUtils.toString(response.getEntity(), "utf-8");
            } else {
                String errMsg = EntityUtils.toString(Objects.requireNonNull(response.getEntity()));
                System.out.println(errMsg);
                throw new IOException(errMsg);
            }
        } finally {
            response.close();
        }
    }

    /**
     *@描述   寻找核心错误提示
     *@参数  java.util.Map<java.lang.String,java.lang.Object>
     *@返回值  java.util.Map<java.lang.String,java.lang.Object>
     */
    private Map<String, Object> handleEsted(Map<String, Object> throwMap) {
        Map<String, Object> cause = (Map<String, Object>) throwMap.get("cause");
        if (cause == null) {
            typeCause = (String) throwMap.get("type");
            messageCause = (String) throwMap.get("message");
            return cause;
        } else {
            return handleEsted((Map<String, Object>) throwMap.get("cause"));
        }
    }

    /**
     *@描述   将错误转化为map类型
     *@参数  java.util.Map<java.lang.String,java.lang.Object>
     *@返回值  java.util.Map<java.lang.String,java.lang.Object>
     */
    private Map<String, Object> throwableToMap(Object throwable) {
        String jsonString = JSON.toJSONString(throwable);
//        json不允许又@的参数
        String replace = jsonString.replace("@", "");
        Map<String, Object> map = JSON.parseObject(replace, Map.class);
        return recursion(map);
    }

    /**
     *@描述   递归处理嵌套问题
     *@参数  java.util.Map<java.lang.String,java.lang.Object>
     *@返回值  java.util.Map<java.lang.String,java.lang.Object>
     */
    private Map<String, Object> recursion(Map<String, Object> map) {
        HashMap<String, Object> resultMap = new HashMap<>();
        if (map.get("beanClass") != null) {
            lastBeanClass = (String) map.get("beanClass");
        }
        resultMap.put("type", map.get("type"));
        resultMap.put("message", map.get("message"));
        Map<String, Object> cause = (Map<String, Object>) map.get("cause");
        if (cause != null) {
//            递归
            resultMap.put("cause", recursion(cause));
        }
        return resultMap;
    }
}