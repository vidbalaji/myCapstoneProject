package org.example.userauthservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class UserDto {
    private Long id;

    private String name;

    private String email;

    private String phoneNumber;

    private List<String> roles;
}
