package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_payment_record")
public class PaymentRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String transactionId;
    private String thirdPartyTradeNo; // 第三方支付平台交易号(支付宝/微信)
    private BigDecimal payAmount;
    private Integer payStatus; // 0-待支付 1-支付成功 2-已退款 3-支付中
    private Integer payChannel; // 1-支付宝 2-微信
    private String channelName; // ALIPAY/WECHAT
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
    private LocalDateTime payExpireTime; // 支付过期时间
    @Version
    private Integer version; // 乐观锁版本号
}
