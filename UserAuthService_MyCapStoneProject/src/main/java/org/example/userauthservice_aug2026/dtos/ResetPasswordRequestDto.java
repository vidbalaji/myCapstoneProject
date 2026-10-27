package org.example.userauthservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ResetPasswordRequestDto {
    private String token;

    private String newPassword;
}
