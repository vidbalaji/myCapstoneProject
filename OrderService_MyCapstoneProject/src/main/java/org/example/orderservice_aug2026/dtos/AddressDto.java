package org.example.orderservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class AddressDto {
    private String fullName;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String pincode;
    private String phoneNumber;
}
