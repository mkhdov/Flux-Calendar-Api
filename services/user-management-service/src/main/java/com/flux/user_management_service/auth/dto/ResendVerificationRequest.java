package com.flux.user_management_service.auth.dto;

import lombok.Data;

@Data
public class ResendVerificationRequest {
    private String email;
}
