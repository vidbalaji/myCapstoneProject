package org.example.userauthservice_aug2026.services;

import org.example.userauthservice_aug2026.exceptions.UserAlreadyExistException;
import org.example.userauthservice_aug2026.exceptions.UserNotRegisteredException;
import org.example.userauthservice_aug2026.models.User;
import org.example.userauthservice_aug2026.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
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

    public User updateProfile(Long id, String name, String phoneNumber) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new UserNotRegisteredException("User with id " + id + " not found"));

        if (name != null && !name.isBlank()) {
            user.setName(name);
        }
        if (phoneNumber != null && !phoneNumber.equals(user.getPhoneNumber())) {
            Optional<User> other = userRepo.findByPhoneNumber(phoneNumber);
            if (other.isPresent()) {
                throw new UserAlreadyExistException("User with phoneNumber " + phoneNumber + " already exists");
            }
            user.setPhoneNumber(phoneNumber);
        }
        user.setLastUpdatedAt(new Date());
        return userRepo.save(user);
    }
}
