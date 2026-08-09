package com.qrserve.merchant.dto;

import lombok.Data;

@Data
public class UpdateWaiterRequest {
    private String status;
    private String shift;
}