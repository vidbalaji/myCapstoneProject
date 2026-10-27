package org.example.orderservice_aug2026.models;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Embeddable
public class Address {
    private String fullName;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String pincode;
    private String phoneNumber;
}
