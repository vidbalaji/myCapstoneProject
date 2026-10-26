package org.example.orderservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class StatusHistoryDto {
    private String status;
    private String note;
    private Date changedAt;
}
