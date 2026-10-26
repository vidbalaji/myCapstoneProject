package org.example.userauthservice_aug2026.services;

import org.example.userauthservice_aug2026.models.User;
import org.example.userauthservice_aug2026.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepo userRepo;

    public User getUserDetailsById(Long id) {
       Optional<User> optionalUser = userRepo.findById(id);
       if(optionalUser.isEmpty()) {
           return null;
       }

       return optionalUser.get();
    }
}
