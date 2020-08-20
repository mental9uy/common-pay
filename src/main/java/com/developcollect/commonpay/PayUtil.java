package com.developcollect.commonpay;

import cn.hutool.core.codec.Base64;
import cn.hutool.extra.qrcode.QrCodeUtil;
import com.developcollect.commonpay.config.AbstractPayConfig;
import com.developcollect.commonpay.config.GlobalConfig;
import com.developcollect.commonpay.exception.PayException;
import com.developcollect.commonpay.pay.*;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付工具类
 * @author zak
 * @since 1.0.0
 */
@Slf4j
public class PayUtil {

    /**
     * 扫用户付款码支付
     * <p>
     * 官方文档:
     * 支付宝  https://opendocs.alipay.com/open/194/105072
     * 微信    https://pay.weixin.qq.com/wiki/doc/api/micropay.php?chapter=9_10&index=1
     *
     * @param payDTO    订单
     * @param authCode 付款码
     */
    public static PayResponse payScan(IPayDTO payDTO, String authCode) {
        Pay pay = GlobalConfig.payFactory().createPay(payDTO.getPayPlatform());
        PayResponse payResponse = pay.payScan(payDTO, authCode);
        return payResponse;
    }

    /**
     * 使用订单中的支付平台, 而是用指定的支付平台进行扫用户付款码支付
     *
     * @param payPlatform 支付平台
     * @param payDTO       订单
     * @param authCode    付款码
     */
    public static PayResponse payScan(int payPlatform, IPayDTO payDTO, String authCode) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        PayResponse payResponse = payScan(rePayPlatformPayDTO, authCode);
        return payResponse;
    }

    /**
     * app支付
     *
     * 官方文档:
     * 支付宝  https://opendocs.alipay.com/open/204/105051
     * 微信    https://pay.weixin.qq.com/wiki/doc/api/app/app.php?chapter=8_1
     * @param payDTO 订单
     * @return com.developcollect.commonpay.pay.PayAppResult
     */
    public static PayAppResult payApp(IPayDTO payDTO) {
        Pay pay = GlobalConfig.payFactory().createPay(payDTO.getPayPlatform());
        PayAppResult result = pay.payApp(payDTO);
        return result;
    }

    /**
     * 不使用订单中的支付平台, 而是用指定的支付平台进行app支付
     *
     * @param payPlatform 支付平台
     * @param payDTO       订单
     * @return com.developcollect.commonpay.pay.PayAppResult
     */
    public static PayAppResult payApp(int payPlatform, IPayDTO payDTO) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        PayAppResult result = payApp(rePayPlatformPayDTO);
        return result;
    }

    /**
     * 支付(二维码)
     * 返回的是二维码的文本值, 可根据该文本值生成二维码图片
     *
     * 官方文档:
     * 支付宝  https://opendocs.alipay.com/open/194/106078
     * 微信    https://pay.weixin.qq.com/wiki/doc/api/native.php?chapter=6_1
     * @param payDTO 订单
     * @return java.lang.String 二维码值
     * @author zak
     * @since 1.0.0
     */
    public static String payQrCode(IPayDTO payDTO) {
        Pay pay = GlobalConfig.payFactory().createPay(payDTO.getPayPlatform());
        String code = pay.payQrCode(payDTO);
        return code;
    }

    /**
     * 不使用订单中的支付平台, 而是用指定的支付平台支付(二维码)
     * 返回的是二维码的文本值, 可根据该文本值生成二维码图片
     *
     * @param payPlatform 支付平台
     * @param payDTO       订单
     * @return java.lang.String 二维码值
     * @author zak
     * @since 1.8.6
     */
    public static String payQrCode(int payPlatform, IPayDTO payDTO) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        return payQrCode(rePayPlatformPayDTO);
    }

    /**
     * 支付(二维码)
     * 返回的是一张png格式的二维码图片的base64字符串
     *
     * @param payDTO 订单
     * @return java.lang.String 二维码图片转base64后的字符串
     * @author zak
     * @since 1.0.0
     */
    public static String payQrCodeBase64(IPayDTO payDTO) {
        String code = payQrCode(payDTO);
        AbstractPayConfig payConfig = GlobalConfig.getPayConfig(payDTO.getPayPlatform());
        int qrCodeWidth = payConfig.getQrCodeWidth();
        int qrCodeHeight = payConfig.getQrCodeHeight();
        byte[] qrCodeBytes = QrCodeUtil.generatePng(code, qrCodeWidth, qrCodeHeight);
        String qrCodeBase64 = Base64.encode(qrCodeBytes);
        return qrCodeBase64;
    }

    /**
     * 不使用订单中的支付平台, 而是用指定的支付平台支付(二维码)
     * 返回的是一张png格式的二维码图片的base64字符串
     *
     * @param payPlatform 支付平台
     * @param payDTO       订单
     * @return java.lang.String 二维码图片转base64后的字符串
     * @author zak
     * @since 1.8.6
     */
    public static String payQrCodeBase64(int payPlatform, IPayDTO payDTO) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        return payQrCodeBase64(rePayPlatformPayDTO);
    }

    /**
     * 支付(二维码)
     * 返回的是二维码图片的访问链接
     * 这个链接是通过配置的链接生成器生成的
     *
     * @param payDTO   订单
     * @return java.lang.String  二维码图片的访问链接
     * @author zak
     * @since 1.0.0
     */
    public static String payQrCodeAccessUrl(IPayDTO payDTO) {
        String code = payQrCode(payDTO);
        String accessUrl = GlobalConfig
                .getPayConfig(payDTO.getPayPlatform())
                .getPayQrCodeAccessUrlGenerator()
                .apply(payDTO, code);
        return accessUrl;
    }

    /**
     * 不使用订单中的支付平台, 而是用指定的支付平台支付(二维码)
     * 返回的是二维码图片的访问链接
     * 这个链接是通过配置的链接生成器生成的
     *
     * @param payDTO 订单
     * @return java.lang.String  二维码图片的访问链接
     * @author zak
     * @since 1.8.6
     */
    public static String payQrCodeAccessUrl(int payPlatform, IPayDTO payDTO) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        return payQrCodeAccessUrl(rePayPlatformPayDTO);
    }

    /**
     * 支付(PC页面跳转方式)
     * 返回的是一段html代码
     *
     * 官方文档:
     * 支付宝  https://opensupport.alipay.com/support/helpcenter/95/201602482184?ant_source=zsearch
     * 微信    不支持
     * @param payDTO 订单
     * @return html代码段
     * @author zak
     * @since 1.0.0
     */
    public static String payPcForm(IPayDTO payDTO) {
        Pay pay = GlobalConfig.payFactory().createPay(payDTO.getPayPlatform());
        String form = pay.payPcForm(payDTO);
        return form;
    }

    /**
     * 不使用订单中的支付平台, 而是用指定的支付平台支付(PC页面跳转方式)
     * 返回的是一段html代码
     *
     * @param payDTO 订单
     * @return html代码段
     * @author zak
     * @since 1.8.6
     */
    public static String payPcForm(int payPlatform, IPayDTO payDTO) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        return payPcForm(rePayPlatformPayDTO);
    }


    /**
     * 支付(PC页面跳转方式)
     * 返回的是一个页面链接
     *
     * @param payDTO 订单
     * @return 支付页面链接
     * @author zak
     * @since 1.0.0
     */
    public static String payPcFormAccessUrl(IPayDTO payDTO) {
        String form = payPcForm(payDTO);
        String accessUrl = GlobalConfig
                .getPayConfig(payDTO.getPayPlatform())
                .getPcPayFormHtmlAccessUrlGenerator()
                .apply(payDTO, form);
        return accessUrl;
    }

    /**
     * 不使用订单中的支付平台, 而是用指定的支付平台支付(PC页面跳转方式)
     * 返回的是一个页面链接
     *
     * @param payPlatform 支付平台
     * @param payDTO       订单
     * @return 支付页面链接
     * @author zak
     * @since 1.8.6
     */
    public static String payPcFormAccessUrl(int payPlatform, IPayDTO payDTO) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        return payPcFormAccessUrl(rePayPlatformPayDTO);
    }


    /**
     * 支付(WAP页面跳转方式)
     * 返回的是一段html代码
     *
     * 官方文档:
     * 支付宝  https://opendocs.alipay.com/apis/api_1/alipay.trade.wap.pay
     * 微信    https://pay.weixin.qq.com/wiki/doc/api/H5.php?chapter=15_1
     * @param payDTO 订单
     * @return html代码段
     * @author zak
     * @since 1.0.0
     */
    public static String payWapForm(IPayDTO payDTO) {
        if (payDTO.getPayPlatform() == PayPlatform.WX_PAY) {
            // 微信是直接返回的url, 没有返回html代码的接口
            throw new PayException("暂不支持返回WAP页面代码");
        }
        Pay pay = GlobalConfig.payFactory().createPay(payDTO.getPayPlatform());
        String form = pay.payWapForm(payDTO);
        return form;
    }

    /**
     * 不使用订单中的支付平台, 而是用指定的支付平台支付(WAP页面跳转方式)
     * 返回的是一段html代码
     *
     * @param payPlatform 支付平台
     * @param payDTO       订单
     * @return html代码段
     * @author zak
     * @since 1.8.6
     */
    public static String payWapForm(int payPlatform, IPayDTO payDTO) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        return payWapForm(rePayPlatformPayDTO);
    }


    /**
     * 支付(WAP页面跳转方式)
     * 返回的是一个页面链接
     *
     * @param payDTO 订单
     * @return 支付页面链接
     * @author zak
     * @since 1.0.0
     */
    public static String payWapFormAccessUrl(IPayDTO payDTO) {
        if (payDTO.getPayPlatform() == PayPlatform.WX_PAY) {
            // 微信是直接返回的url, 不需要通过访问链接生成器生成
            Pay pay = GlobalConfig.payFactory().createPay(payDTO.getPayPlatform());
            String form = pay.payWapForm(payDTO);
            return form;
        }

        String form = payWapForm(payDTO);
        String accessUrl = GlobalConfig
                .getPayConfig(payDTO.getPayPlatform())
                .getWapPayFormHtmlAccessUrlGenerator()
                .apply(payDTO, form);
        return accessUrl;
    }

    /**
     * 不使用订单中的支付平台, 而是用指定的支付平台支付(WAP页面跳转方式)
     * 返回的是一个页面链接
     *
     * @param payPlatform 支付平台
     * @param payDTO       订单
     * @return 支付页面链接
     * @author zak
     * @since 1.8.6
     */
    public static String payWapFormAccessUrl(int payPlatform, IPayDTO payDTO) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        return payWapFormAccessUrl(rePayPlatformPayDTO);
    }

    /**
     * 在微信浏览器里面使用WeixinJSBridge打开H5网页中执行JS调起支付
     * 仅微信支持
     * <p>
     * 官方文档:
     * 支付宝  不支持
     * 微信    https://pay.weixin.qq.com/wiki/doc/api/jsapi.php?chapter=7_1
     *
     * @param payDTO  订单
     * @param openId 微信用户标识
     * @return WxJsPayResult
     */
    public static PayWxJsResult payWxJs(IPayDTO payDTO, String openId) {
        Pay pay = GlobalConfig.payFactory().createPay(payDTO.getPayPlatform());
        PayWxJsResult payWxJsResult = pay.payWxJs(payDTO, openId);
        return payWxJsResult;
    }

    /**
     * 不使用订单中的支付平台, 而是用指定的支付平台在微信浏览器里面使用WeixinJSBridge打开H5网页中执行JS调起支付
     * 仅微信支持
     *
     * @param payPlatform 支付平台
     * @param payDTO       订单
     * @param openId      微信用户标识
     * @return WxJsPayResult
     * @author zak
     * @since 1.8.6
     */
    public static PayWxJsResult payWxJs(int payPlatform, IPayDTO payDTO, String openId) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        return payWxJs(rePayPlatformPayDTO, openId);
    }

    /**
     * 支付(同步支付)
     * 直接返回支付结果,而不是通过异步通知的形式
     *
     * @param payDTO 订单
     * @return PayResponse 支付结果
     * @author zak
     * @since 1.0.0
     */
    public static PayResponse paySync(IPayDTO payDTO) {
        Pay pay = GlobalConfig.payFactory().createPay(payDTO.getPayPlatform());
        PayResponse payResponse = pay.paySync(payDTO);
        // 如果广播失败了不会重试
        boolean broadcast = GlobalConfig.payBroadcaster().broadcast(payResponse);
        if (!broadcast) {
            log.error("订单[{}]支付结果广播失败", payDTO.getOutTradeNo());
        }
        return payResponse;
    }

    /**
     * 不使用订单中的支付平台, 而是用指定的支付平台支付(同步支付)
     * 直接返回支付结果,而不是通过异步通知的形式
     *
     * @param payPlatform 支付平台
     * @param payDTO       订单
     * @return 支付结果
     * @author zak
     * @since 1.8.6
     */
    public static PayResponse paySync(int payPlatform, IPayDTO payDTO) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        return paySync(rePayPlatformPayDTO);
    }

    /**
     * 查询订单支付结果
     * 查询出错时返回null
     *
     * @param payDTO 订单
     * @return 订单支付结果
     * @author zak
     * @since 1.0.0
     */
    public static PayResponse payQuery(IPayDTO payDTO) {
        Pay pay = GlobalConfig.payFactory().createPay(payDTO.getPayPlatform());
        PayResponse payResponse = pay.payQuery(payDTO);
        return payResponse;
    }

    /**
     * 不使用订单中的支付平台, 而是用指定的支付平台查询订单支付结果
     * 查询出错时返回null
     *
     * @param payPlatform 支付平台
     * @param payDTO       订单
     * @return 支付结果
     * @author zak
     * @since 1.8.6
     */
    public static PayResponse payQuery(int payPlatform, IPayDTO payDTO) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        return payQuery(rePayPlatformPayDTO);
    }


    /**
     * 退款(同步方法)
     * 直接返回退款结果,而不是通过异步通知的形式
     *
     * 官方文档:
     * 支付宝  https://opendocs.alipay.com/apis/api_1/alipay.trade.refund
     * 微信    https://pay.weixin.qq.com/wiki/doc/api/jsapi.php?chapter=9_4
     * @param payDTO 订单对象
     * @param refundDTO 退款对象
     * @return RefundResponse 退款结果
     * @author zak
     * @since 1.0.0
     */
    public static RefundResponse refundSync(IPayDTO payDTO, IRefundDTO refundDTO) {
        Pay pay = GlobalConfig.payFactory().createPay(refundDTO.getPayPlatform());
        RefundResponse refundResponse = pay.refundSync(payDTO, refundDTO);
        if (GlobalConfig.refundBroadcaster() != null) {
            // 如果广播失败了不会重试
            boolean broadcast = GlobalConfig.refundBroadcaster().broadcast(refundResponse);
            if (!broadcast) {
                log.error("退款[{}]结果广播失败", refundDTO.getOutRefundNo());
            }
        }
        return refundResponse;
    }

    /**
     * 不使用退款对象中的支付平台, 而是用指定的支付平台退款(同步方法)
     * 直接返回退款结果,而不是通过异步通知的形式
     *
     * @param payPlatform 支付平台
     * @param payDTO      支付参数
     * @param refundDTO      退款对象
     * @return 退款结果
     * @author zak
     * @since 1.8.6
     */
    public static RefundResponse refundSync(int payPlatform, IPayDTO payDTO, IRefundDTO refundDTO) {
        RePayPlatformPayDTO rePayPlatformPayDTO = new RePayPlatformPayDTO(payPlatform, payDTO);
        RePayPlatformRefundDTO rePayPlatformRefundDTO = new RePayPlatformRefundDTO(payPlatform, refundDTO);
        return refundSync(rePayPlatformPayDTO, rePayPlatformRefundDTO);
    }


    /**
     * 转账(同步方法)
     * 直接返回转账结果,而不是通过异步通知的形式
     *
     * 官方文档:
     * 支付宝  https://opendocs.alipay.com/open/309/106235
     * 微信    https://pay.weixin.qq.com/wiki/doc/api/tools/mch_pay.php?chapter=14_1
     * @param transferDTO 转账对象
     * @return TransferResponse 转账结果
     * @author zak
     * @since 1.0.0
     */
    public static TransferResponse transferSync(ITransferDTO transferDTO) {
        Pay pay = GlobalConfig.payFactory().createPay(transferDTO.getPayPlatform());
        TransferResponse transferResponse = pay.transferSync(transferDTO);

        if (GlobalConfig.transferBroadcaster() != null) {
            // 如果广播失败了不会重试
            boolean broadcast = GlobalConfig.transferBroadcaster().broadcast(transferResponse);
            if (!broadcast) {
                log.error("转账[{}]结果广播失败", transferDTO.getOutTransferNo());
            }
        }
        return transferResponse;
    }


    /**
     * 不使用转账对象中的支付平台, 而是用指定的支付平台转账(同步方法)
     * 直接返回转账结果,而不是通过异步通知的形式
     *
     * @param payPlatform 支付平台
     * @param transferDTO    转账对象
     * @return TransferResponse 转账结果
     * @author zak
     * @since 1.8.6
     */
    public static TransferResponse transferSync(int payPlatform, ITransferDTO transferDTO) {
        RePayPlatformTransferDTO rePayPlatformTransferDTO = new RePayPlatformTransferDTO(payPlatform, transferDTO);
        return transferSync(rePayPlatformTransferDTO);
    }
}
