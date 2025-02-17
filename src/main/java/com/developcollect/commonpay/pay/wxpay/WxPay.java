package com.developcollect.commonpay.pay.wxpay;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson.JSONObject;
import com.developcollect.commonpay.ExtKeys;
import com.developcollect.commonpay.PayPlatform;
import com.developcollect.commonpay.config.WxPayConfig;
import com.developcollect.commonpay.exception.PayException;
import com.developcollect.commonpay.pay.*;
import com.developcollect.commonpay.pay.wxpay.sdk.WXPay;
import com.developcollect.commonpay.pay.wxpay.sdk.WXPayConstants;
import com.developcollect.commonpay.pay.wxpay.sdk.WXPayUtil;
import com.developcollect.dcinfra.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信支付
 * https://pay.weixin.qq.com/wiki/doc/api/native.php?chapter=9_1
 *
 * @author zak
 * @since 1.0.0
 */
@Slf4j
public class WxPay extends AbstractPay {


    private WXPay getWxSdkPay(WxPayConfig wxPayConfig) {
        DefaultWXPayConfig wxSdkConfig = new DefaultWXPayConfig();
        wxSdkConfig.setAppId(wxPayConfig.getAppId());
        wxSdkConfig.setMchId(wxPayConfig.getMchId());
        wxSdkConfig.setKey(wxPayConfig.getKey());

        WXPay wxPay = new WXPay(wxSdkConfig, true, wxPayConfig.isDebug());
        return wxPay;
    }

    private Map<String, String> convertToPayReqMap(IPayDTO payDTO) {
        Map<String, String> reqData = new HashMap<>(16);
        reqData.put("body", "商品_" + payDTO.getOutTradeNo());
        reqData.put("out_trade_no", payDTO.getOutTradeNo());
        // 实付款 = 订单总额 + 运费 - 折扣
        reqData.put("total_fee", String.valueOf(payDTO.getTotalFee()));
        // 这个ip好像可以随便填
        reqData.put("spbill_create_ip", "117.43.68.32");
        if (payDTO.getTimeStart() != null) {
            reqData.put("time_start", DateUtil.format(payDTO.getTimeStart(), "yyyyMMddHHmmss"));
        }
        // time_expire只能第一次下单传值，不允许二次修改，二次修改微信接口将报错。
        // 目前根据订单中是否有微信支付订单号判断是否已下单
        if (payDTO.getTimeExpire() != null && payDTO.getTradeNo() == null) {
            reqData.put("time_expire", DateUtil.format(payDTO.getTimeExpire(), "yyyyMMddHHmmss"));
        }

        return reqData;
    }

    private Map<String, String> convertToRefundReqMap(IPayDTO payDTO, IRefundDTO refund) {
        Map<String, String> reqData = new HashMap<>(16);
        // 商户系统内部订单号，要求32个字符内，只能是数字、大小写字母_-|*@ ，且在同一个商户号下唯一。
        //transaction_id、out_trade_no二选一，如果同时存在优先级：transaction_id> out_trade_no
        reqData.put("out_trade_no", payDTO.getOutTradeNo());
        // 商户系统内部的退款单号，商户系统内部唯一，只能是数字、大小写字母_-|*@ ，同一退款单号多次请求只退一笔
        reqData.put("out_refund_no", refund.getOutRefundNo());
        // 订单总金额，单位为分，只能为整数，详见支付金额
        reqData.put("total_fee", String.valueOf(payDTO.getTotalFee()));
        // 退款总金额，单位为分，只能为整数，详见支付金额
        reqData.put("refund_fee", String.valueOf(refund.getRefundFee()));
        // 异步接收微信支付退款结果通知的回调地址，通知URL必须为外网可访问的url，不允许带参数

        // 若商户传入，会在下发给用户的退款消息中体现退款原因
        // 注意：若订单退款金额≤1元，且属于部分退款，则不会在退款消息中体现退款原因
//        reqData.put("refund_desc", "");
        return reqData;
    }

    private Map<String, String> convertToTransferReqMap(ITransferDTO transfer) {
        Map<String, String> reqData = new HashMap<>(16);
        // 商户订单号，需保持唯一性(只能是字母或者数字，不能包含有其它字符)
        reqData.put("partner_trade_no", transfer.getOutTransferNo());
        // 商户appid下，某用户的openid
        reqData.put("openid", transfer.getAccount());
        // NO_CHECK：不校验真实姓名 FORCE_CHECK：强校验真实姓名
        reqData.put("check_name", transfer.needCheckName() ? "FORCE_CHECK" : "NO_CHECK");
        // 收款用户真实姓名。如果check_name设置为FORCE_CHECK，则必填用户真实姓名
        if (StrUtil.isNotBlank(transfer.getReUserName())) {
            reqData.put("re_user_name", transfer.getReUserName());
        }
        // 企业付款备注，必填。注意：备注中的敏感词会被转成字符*
        reqData.put("desc", transfer.getDescription());
        // 企业付款金额，单位为分
        reqData.put("amount", String.valueOf(transfer.getAmount()));
        // 该IP同在商户平台设置的IP白名单中的IP没有关联，该IP可传用户端或者服务端的IP。
        reqData.put("spbill_create_ip", "10.2.3.10");
        return reqData;
    }

    /**
     * 微信统一下单
     *
     * @param payDTO       订单
     * @param wxPayConfig 微信支付配置
     * @param tradeType   交易类型
     *                    JSAPI -JSAPI支付
     *                    NATIVE -Native支付
     *                    APP -APP支付
     * @return java.util.Map<java.lang.String, java.lang.String>
     * @author Zhu Kaixiao
     * @date 2020/8/15 14:18
     */
    private Map<String, String> unifiedOrder(IPayDTO payDTO, WxPayConfig wxPayConfig, String tradeType, String openId) throws Exception {
        WXPay wxSdkPay = getWxSdkPay(wxPayConfig);
        Map<String, String> reqData = convertToPayReqMap(payDTO);
        reqData.put("trade_type", tradeType);
        if (StrUtil.isNotBlank(openId)) {
            // trade_type=JSAPI时（即JSAPI支付），此参数必传，此参数为微信用户在商户对应appid下的唯一标识。
            reqData.put("openid", openId);
        }
        reqData.put("notify_url", wxPayConfig.getPayNotifyUrlGenerator().apply(payDTO));

        if (log.isDebugEnabled()) {
            log.debug("微信支付参数:{}", JSONObject.toJSONString(reqData));
        }

        Map<String, String> map = wxSdkPay.unifiedOrder(reqData);

        if ("FAIL".equals(map.get("return_code"))) {
            throw new PayException(map.get("return_msg"), map);
        }
        if ("FAIL".equals(map.get("result_code"))) {
            throw new PayException(map.get("err_code_des"), map);
        }
        return map;
    }

    @Override
    public PayResponse payScan(IPayDTO payDTO) {
        try {
            WxPayConfig wxPayConfig = getPayConfig();
            WXPay wxSdkPay = getWxSdkPay(wxPayConfig);
            Map<String, String> reqData = convertToPayReqMap(payDTO);
            reqData.put("auth_code", payDTO.getExt(ExtKeys.PAY_SCAN_AUTH_CODE).toString());
            Map<String, String> map = wxSdkPay.microPay(reqData);
            if ("FAIL".equals(map.get("return_code"))) {
                throw new PayException(map.get("return_msg"), map);
            }


            PayResponse payResponse = new PayResponse();
            payResponse.setPayPlatform(getPlatform());
            payResponse.setSuccess("SUCCESS".equals(map.get("result_code")));
            payResponse.setErrCode(map.get("err_code"));
            payResponse.setErrCodeDes(map.get("err_code_des"));
            payResponse.setTradeNo(map.get("transaction_id"));
            payResponse.setOutTradeNo(map.get("out_trade_no"));
            payResponse.setPayTime(DateUtil.parseLocalDateTime(map.get("time_end"), "yyyyMMddHHmmss"));
            payResponse.setRawObj((Serializable) map);

            return payResponse;
        } catch (Throwable throwable) {
            log.error("微信APP支付失败");
            throw throwable instanceof PayException
                    ? (PayException) throwable
                    : new PayException("微信APP支付失败", throwable);
        }
    }

    /**
     * app支付
     * <p>
     * https://pay.weixin.qq.com/wiki/doc/api/app/app.php?chapter=8_3
     *
     * @param payDTO
     */
    @Override
    public PayAppResult payApp(IPayDTO payDTO) {

        try {
            WxPayConfig wxPayConfig = getPayConfig();
            Map<String, String> map = unifiedOrder(payDTO, wxPayConfig, "APP", null);

            String prepayId = map.get("prepay_id");

            Map<String, String> signParam = new HashMap<>(8);
            signParam.put("package", "Sign=WXPay");
            signParam.put("partnerid", wxPayConfig.getMchId());
            signParam.put("appid", wxPayConfig.getAppId());
            signParam.put("nonce_str", WXPayUtil.generateNonceStr());
            signParam.put("timeStamp", String.valueOf((System.currentTimeMillis() / 1000)));
            signParam.put("sign_type", wxPayConfig.isDebug() ? WXPayConstants.MD5 : WXPayConstants.HMACSHA256);
            signParam.put("sign", WXPayUtil.generateSignature(signParam, wxPayConfig.getKey(),
                    wxPayConfig.isDebug() ? WXPayConstants.SignType.MD5 : WXPayConstants.SignType.HMACSHA256));
            signParam.put("prepay_id", prepayId);

            PayAppResult payAppResult = new PayAppResult();
            payAppResult.setAppId(signParam.get("appid"));
            payAppResult.setPartnerId(signParam.get("partnerid"));
            payAppResult.setTimeStamp(signParam.get("timeStamp"));
            payAppResult.setNonceStr(signParam.get("nonce_str"));
            payAppResult.setPackage0(signParam.get("package"));
            payAppResult.setPrepayId(signParam.get("prepay_id"));
            payAppResult.setSignType(signParam.get("sign_type"));
            payAppResult.setPaySign(signParam.get("sign"));

            return payAppResult;
        } catch (Throwable throwable) {
            log.error("微信APP支付失败");
            throw throwable instanceof PayException
                    ? (PayException) throwable
                    : new PayException("微信APP支付失败", throwable);
        }
    }

    /**
     * 微信扫码支付
     * https://pay.weixin.qq.com/wiki/doc/api/native.php?chapter=9_1
     *
     * @param payDTO
     * @return java.lang.String
     */
    @Override
    public String payQrCode(IPayDTO payDTO) {
        try {
            WxPayConfig wxPayConfig = getPayConfig();
            Map<String, String> map = unifiedOrder(payDTO, wxPayConfig, "NATIVE", null);

            String codeUrl = map.get("code_url");
            log.debug("微信支付,code_url: {}", codeUrl);
            return codeUrl;
        } catch (Throwable throwable) {
            log.error("微信支付失败");
            throw throwable instanceof PayException
                    ? (PayException) throwable
                    : new PayException("微信支付失败", throwable);
        }
    }

    /**
     * 微信js支付
     * https://pay.weixin.qq.com/wiki/doc/api/jsapi.php?chapter=7_7&index=6
     *
     * @param payDTO
     * @return com.developcollect.commonpay.pay.WxJsPayResult
     */
    @Override
    public PayWxJsResult payWxJs(IPayDTO payDTO) {
        try {
            WxPayConfig wxPayConfig = getPayConfig();
            Map<String, String> map = unifiedOrder(payDTO, wxPayConfig, "JSAPI",
                    payDTO.getExt(ExtKeys.PAY_WXJS_OPENID).toString());
            String prepayId = map.get("prepay_id");

            Map<String, String> wxJsPayMap = new HashMap<>(8);
            wxJsPayMap.put("package", "prepay_id=" + prepayId);
            // 这里是深坑，微信js支付时有两次签名，第一次在统一下单处，然后用统一下单的返回的prepay_id再做一次签名
            // 这两次签名的参数名风格不同，前面的是下滑线风格，这里是驼峰风格，这里的appId的I要大写
            wxJsPayMap.put("appId", wxPayConfig.getAppId());
            wxJsPayMap.put("nonceStr", WXPayUtil.generateNonceStr());
            wxJsPayMap.put("timeStamp", String.valueOf((System.currentTimeMillis() / 1000)));
            wxJsPayMap.put("signType", wxPayConfig.isDebug() ? WXPayConstants.MD5 : WXPayConstants.HMACSHA256);
            wxJsPayMap.put("sign", WXPayUtil.generateSignature(wxJsPayMap, wxPayConfig.getKey(),
                    wxPayConfig.isDebug() ? WXPayConstants.SignType.MD5 : WXPayConstants.SignType.HMACSHA256));
            wxJsPayMap.put("prepayId", prepayId);

            PayWxJsResult payWxJsResult = PayWxJsResult.of(wxJsPayMap);
            return payWxJsResult;
        } catch (Throwable throwable) {
            log.error("微信支付失败");
            throw throwable instanceof PayException
                    ? (PayException) throwable
                    : new PayException("微信支付失败", throwable);
        }
    }

    @Override
    public PayWxJsResult payAppletsJs(IPayDTO payDTO) {
        try {
            WxPayConfig wxPayConfig = getPayConfig();
            wxPayConfig.setAppId(wxPayConfig.getAppletAppid());
            Map<String, String> map = unifiedOrder(payDTO, wxPayConfig, "JSAPI",
                    payDTO.getExt(ExtKeys.PAY_WXJS_OPENID).toString());
            String prepayId = map.get("prepay_id");

            Map<String, String> wxJsPayMap = new HashMap<>(8);
            wxJsPayMap.put("package", "prepay_id=" + prepayId);
            // 这里是深坑，微信js支付时有两次签名，第一次在统一下单处，然后用统一下单的返回的prepay_id再做一次签名
            // 这两次签名的参数名风格不同，前面的是下滑线风格，这里是驼峰风格，这里的appId的I要大写
            wxJsPayMap.put("appId", wxPayConfig.getAppId());
            wxJsPayMap.put("nonceStr", WXPayUtil.generateNonceStr());
            wxJsPayMap.put("timeStamp", String.valueOf((System.currentTimeMillis() / 1000)));
            wxJsPayMap.put("signType", wxPayConfig.isDebug() ? WXPayConstants.MD5 : WXPayConstants.HMACSHA256);
            wxJsPayMap.put("sign", WXPayUtil.generateSignature(wxJsPayMap, wxPayConfig.getKey(),
                    wxPayConfig.isDebug() ? WXPayConstants.SignType.MD5 : WXPayConstants.SignType.HMACSHA256));
            wxJsPayMap.put("prepayId", prepayId);

            PayWxJsResult payWxJsResult = PayWxJsResult.of(wxJsPayMap);
            return payWxJsResult;
        } catch (Throwable throwable) {
            log.error("微信支付失败");
            throw throwable instanceof PayException
                    ? (PayException) throwable
                    : new PayException("微信支付失败", throwable);
        }
    }

    /**
     * 微信客户端外的移动端网页支付
     * https://pay.weixin.qq.com/wiki/doc/api/H5.php?chapter=15_4
     *
     * @param payDTO
     * @return java.lang.String
     * @author Zhu Kaixiao
     * @date 2020/8/15 14:23
     */
    @Override
    public String payWapForm(IPayDTO payDTO) {
        try {
            WxPayConfig wxPayConfig = getPayConfig();
            Map<String, String> map = unifiedOrder(payDTO, wxPayConfig, "MWEB", null);

            String mwebUrl = map.get("mweb_url");

            if (wxPayConfig.getWapReturnUrlGenerator() != null) {
                mwebUrl = mwebUrl + "&redirect_url=" + URLUtil.encode(wxPayConfig.getWapReturnUrlGenerator().apply(payDTO));
            }

            log.debug("微信支付,mweb_url: {}", mwebUrl);
            return mwebUrl;
        } catch (Throwable throwable) {
            log.error("微信WAP支付失败");
            throw throwable instanceof PayException
                    ? (PayException) throwable
                    : new PayException("微信WAP支付失败", throwable);
        }
    }

    /**
     * 微信订单查询
     * https://pay.weixin.qq.com/wiki/doc/api/native.php?chapter=9_2
     *
     * @param payDTO
     * @return
     */
    @Override
    public PayResponse payQuery(IPayDTO payDTO) {
        try {
            WxPayConfig payConfig = getPayConfig();
            WXPay wxSdkPay = getWxSdkPay(payConfig);
            Map<String, String> reqData = convertToPayQueryMap(payDTO);
            Map<String, String> map = wxSdkPay.orderQuery(reqData);

            if ("FAIL".equals(map.get("return_code"))) {
                throw new PayException(map.get("return_msg"), map);
            }
            if ("FAIL".equals(map.get("result_code"))) {
                throw new PayException(map.get("err_code_des"), map);
            }
            // --
            PayResponse payResponse = new PayResponse();
            payResponse.setSuccess("SUCCESS".equals(map.get("trade_state")));
            payResponse.setTradeNo(map.get("transaction_id"));
            payResponse.setOutTradeNo(map.get("out_trade_no"));
            payResponse.setPayPlatform(getPlatform());
            payResponse.setPayTime(DateUtil.parseLocalDateTime(map.get("time_end"), "yyyyMMddHHmmss"));
            payResponse.setRawObj((Serializable) map);
            return payResponse;
        } catch (Exception e) {
            log.error("微信订单[{}]查询失败", payDTO.getOutTradeNo(), e);
            return null;
        }
    }

    private Map<String, String> convertToPayQueryMap(IPayDTO payDTO) {
        Map<String, String> reqData = new HashMap<>(16);
        reqData.put("out_trade_no", payDTO.getOutTradeNo());
        return reqData;
    }

    /**
     * 退款
     * https://pay.weixin.qq.com/wiki/doc/api/native.php?chapter=9_4
     *
     * @param payDTO
     * @param refundDTO
     * @return com.developcollect.commonpay.pay.RefundResponse
     * @author Zhu Kaixiao
     * @date 2020/9/28 13:37
     */
    @Override
    public RefundResponse refundSync(IPayDTO payDTO, IRefundDTO refundDTO) {
        try {
            WxPayConfig wxPayConfig = getPayConfig();
            WXPay wxSdkPay = getWxSdkPay(wxPayConfig);

            Map<String, String> reqData = convertToRefundReqMap(payDTO, refundDTO);

            // 如果参数中传了notify_url，则商户平台上配置的回调地址将不会生效。
            if (wxPayConfig.getRefundNotifyUrlGenerator() != null) {
                reqData.put("notify_url", wxPayConfig.getRefundNotifyUrlGenerator().apply(payDTO, refundDTO));
            }

            if (log.isDebugEnabled()) {
                log.debug("微信退款参数:{}", JSONObject.toJSONString(reqData));
            }

            Map<String, String> map = wxSdkPay.refund(reqData);
            if ("FAIL".equals(map.get("return_code"))) {
                throw new PayException(map.get("return_msg"), map);
            }
            if ("FAIL".equals(map.get("result_code"))) {
                throw new PayException(map.get("err_code_des"), map);
            }
            RefundResponse refundResponse = new RefundResponse();
            refundResponse.setRefundNo(map.get("refund_id"));
            refundResponse.setOutRefundNo(map.get("out_refund_no"));
            refundResponse.setPayPlatform(getPlatform());
            // 微信退款需要查询状态
            refundResponse.setStatus(RefundResponse.PROCESSING);
            refundResponse.setRawObj((Serializable) map);
            return refundResponse;
        } catch (Throwable throwable) {
            log.error("微信退款失败");
            throw throwable instanceof PayException
                    ? (PayException) throwable
                    : new PayException("微信退款失败", throwable);
        }
    }


    /**
     * 查询退款结果
     * https://pay.weixin.qq.com/wiki/doc/api/native.php?chapter=9_5
     *
     * @param refundDTO
     * @return com.developcollect.commonpay.pay.RefundResponse
     * @author Zhu Kaixiao
     * @date 2020/12/3 15:55
     */
    @Override
    public RefundResponse refundQuery(IRefundDTO refundDTO) {
        try {
            WxPayConfig wxPayConfig = getPayConfig();
            WXPay wxSdkPay = getWxSdkPay(wxPayConfig);

            Map<String, String> paramMap = new HashMap<>();
            if (StringUtils.isNotBlank(refundDTO.getOutRefundNo())) {
                paramMap.put("out_refund_no", refundDTO.getOutRefundNo());
            }
            if (StringUtils.isNotBlank(refundDTO.getRefundNo())) {
                paramMap.put("refund_id", refundDTO.getRefundNo());
            }
            Map<String, String> resultMap = wxSdkPay.refundQuery(paramMap);
            if (!"SUCCESS".equals(resultMap.get("return_code"))) {
                throw new PayException("微信退款查询接口调用失败：" + resultMap.get("return_msg"), resultMap);
            }

            RefundResponse refundResponse = new RefundResponse();
            refundResponse.setPayPlatform(getPlatform());

            //不存在退款订单记录直接返回
            if (resultMap.containsKey("err_code") && resultMap.get("err_code").equals("REFUNDNOTEXIST")) {
                refundResponse.setRawObj((Serializable) resultMap);
                refundResponse.setOutRefundNo(refundDTO.getOutRefundNo());
                return refundResponse;
            }

            // 确认当前查询的退款的下标
            int refundIdx;
            int refundCount = Integer.parseInt(resultMap.get("refund_count"));
            for (refundIdx = 0; refundIdx < refundCount; refundIdx++) {
                if (refundDTO.getOutRefundNo().equals(resultMap.get("out_refund_no_" + refundIdx))) {
                    resultMap.put("refundIndex", String.valueOf(refundIdx));
                    break;
                }
            }


            refundResponse.setRefundNo(resultMap.get("refund_id_" + refundIdx));
            refundResponse.setOutRefundNo(resultMap.get("out_refund_no_" + refundIdx));
            refundResponse.setRawObj((Serializable) resultMap);

            String refundStatus = resultMap.get("refund_status_" + refundIdx);
            if ("SUCCESS".equals(refundStatus)) {
                refundResponse.setStatus(RefundResponse.SUCCESS);
                refundResponse.setRefundTime(com.developcollect.dcinfra.utils.DateUtil.parseLocalDateTime(resultMap.get("refund_success_time_" + refundIdx), "yyyy-MM-dd HH:mm:ss"));
            } else if ("PROCESSING".equals(refundStatus)) {
                refundResponse.setStatus(RefundResponse.PROCESSING);
            } else {
                refundResponse.setStatus(RefundResponse.FAIL);
            }


            return refundResponse;
        } catch (Throwable throwable) {
            log.error("微信退款状态查询失败");
            throw throwable instanceof PayException
                    ? (PayException) throwable
                    : new PayException("微信退款状态查询失败", throwable);
        }
    }

    /**
     * 微信转账
     * https://pay.weixin.qq.com/wiki/doc/api/tools/mch_pay.php?chapter=14_2
     * 用于企业向微信用户个人付款
     * 目前支持向指定微信用户的openid付款。
     * （获取openid参见微信公众平台开发者文档： https://developers.weixin.qq.com/doc/offiaccount/OA_Web_Apps/Wechat_webpage_authorization.html）
     */
    @Override
    public TransferResponse transferSync(ITransferDTO transferDTO) {
        try {
            WxPayConfig wxPayConfig = getPayConfig();
            WXPay wxSdkPay = getWxSdkPay(wxPayConfig);


            Map<String, String> reqData = convertToTransferReqMap(transferDTO);

            if (log.isDebugEnabled()) {
                log.debug("微信转账参数:{}", JSONObject.toJSONString(reqData));
            }

            Map<String, String> map = wxSdkPay.transfer(reqData);
            if ("FAIL".equals(map.get("return_code"))) {
                throw new PayException(map.get("return_msg"), map);
            }
            TransferResponse transferResponse = new TransferResponse();
            transferResponse.setRawObj((Serializable) map);
            transferResponse.setPayPlatform(getPlatform());


            if ("FAIL".equals(map.get("result_code"))) {
                transferResponse.setOutTransferNo(transferDTO.getOutTransferNo());
                transferResponse.setErrorCode(map.get("err_code"));
                transferResponse.setErrorDesc(map.get("err_code_des"));
                transferResponse.setStatus(TransferResponse.PROCESSING);
            } else {
                transferResponse.setTransferNo(map.get("payment_no"));
                transferResponse.setPaymentTime(DateUtil.parseLocalDateTime(map.get("payment_time")));
                transferResponse.setOutTransferNo(map.get("partner_trade_no"));
                transferResponse.setStatus(TransferResponse.SUCCESS);
            }
            return transferResponse;
        } catch (Throwable throwable) {
            log.error("微信转账失败", throwable);
            throw throwable instanceof PayException
                    ? (PayException) throwable
                    : new PayException("微信转账失败", throwable);
        }

    }


    @Override
    public TransferResponse transferQuery(ITransferDTO transferDTO) {
        try {
            WxPayConfig wxPayConfig = getPayConfig();
            WXPay wxSdkPay = getWxSdkPay(wxPayConfig);

            Map<String, String> reqData = new HashMap<>();
            reqData.put("partner_trade_no", transferDTO.getOutTransferNo());

            Map<String, String> resultMap = wxSdkPay.transferQuery(reqData);

            if ("FAIL".equals(resultMap.get("return_code"))) {
                throw new PayException("微信查询企业付款失败: " + resultMap.get("return_msg"), resultMap);
            }


            TransferResponse transferResponse = new TransferResponse();
            transferResponse.setRawObj((Serializable) resultMap);
            transferResponse.setPayPlatform(getPlatform());

            if ("FAIL".equals(resultMap.get("result_code"))) {
                transferResponse.setOutTransferNo(transferDTO.getOutTransferNo());
                transferResponse.setErrorCode(resultMap.get("err_code"));
                transferResponse.setErrorDesc(resultMap.get("err_code_des"));
                transferResponse.setStatus(TransferResponse.PROCESSING);
            } else {
                transferResponse.setTransferNo(resultMap.get("detail_id"));
                transferResponse.setPaymentTime(DateUtil.parseLocalDateTime(resultMap.get("payment_time")));
                transferResponse.setOutTransferNo(resultMap.get("partner_trade_no"));
                if ("SUCCESS".equals(resultMap.get("status"))) {
                    transferResponse.setStatus(TransferResponse.SUCCESS);
                } else if ("PROCESSING".equals(resultMap.get("status"))) {
                    transferResponse.setStatus(TransferResponse.PROCESSING);
                } else {
                    transferResponse.setStatus(TransferResponse.FAIL);
                    transferResponse.setErrorDesc(resultMap.get("reason"));
                }

            }
            return transferResponse;
        } catch (Throwable throwable) {
            log.error("微信查询企业付款失败", throwable);
            throw throwable instanceof PayException
                    ? (PayException) throwable
                    : new PayException("微信查询企业付款失败", throwable);
        }
    }

    @Override
    protected int getPlatform() {
        return PayPlatform.WX_PAY;
    }
}
