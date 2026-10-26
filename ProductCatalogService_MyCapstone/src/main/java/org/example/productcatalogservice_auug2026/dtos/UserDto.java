package org.example.productcatalogservice_auug2026.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class UserDto {
    private Long id;

    private String name;

    private String email;

    private List<String> roles;
}
