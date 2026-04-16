NEW_FILE_CODE
package com.example.common.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymentRequest {
    private String orderNo;
    private String transactionId;
    private BigDecimal payAmount;
    private Integer payChannel;
}
