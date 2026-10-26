package org.example.orderservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class TrackingDto {
    private Long orderId;
    private String currentStatus;
    private List<StatusHistoryDto> history;
}
