package org.example.userauthservice_aug2026.repos;

import org.example.userauthservice_aug2026.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepo extends JpaRepository<User,Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByEmailAndPassword(String  email,String password);

    Optional<User> findById(Long id);
}
