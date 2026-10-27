package org.example.userauthservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UpdateProfileRequestDto {
    private String name;

    private String phoneNumber;
}
