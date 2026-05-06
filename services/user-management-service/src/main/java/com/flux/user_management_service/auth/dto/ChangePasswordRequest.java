package com.flux.user_management_service.auth.dto;

public record ChangePasswordRequest(
        String oldPassword,
        String currentPassword,
        String repeatedPassword
) {

}
