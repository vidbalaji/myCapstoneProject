package org.example.userauthservice_aug2026.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
@Entity
public class PasswordResetToken extends BaseModel {
    @Column(unique = true)
    private String token;

    @ManyToOne
    private User user;

    private Date expiresAt;
}
