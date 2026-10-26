package org.example.userauthservice_aug2026.services;

import org.antlr.v4.runtime.misc.Pair;
import org.example.userauthservice_aug2026.models.User;

public interface IAuthService {
    User signup(String name,String email,String password,String phoneNumber);

    Pair<User,String> login(String email, String password);

    Boolean validateToken(String token);
}
