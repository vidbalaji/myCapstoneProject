package org.example.userauthservice_aug2026.repos;

import org.example.userauthservice_aug2026.models.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SessionRepo extends JpaRepository<UserSession,Long> {
    Optional<UserSession> findByToken(String token);

    List<UserSession> findByUser_Id(Long userId);

    @Transactional
    void deleteByUser_Id(Long userId);
}
