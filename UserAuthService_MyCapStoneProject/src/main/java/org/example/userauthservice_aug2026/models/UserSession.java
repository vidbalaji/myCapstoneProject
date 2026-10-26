package org.example.userauthservice_aug2026.models;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class UserSession extends BaseModel {
    private String token;

    @ManyToOne
    private User user;
}

//1             1
//session      user
//m              1
//
//m : 1 = m sessions to 1 user
