package org.example.orderservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class UpdateStatusRequestDto {
    private String status;
    private String note;
}
