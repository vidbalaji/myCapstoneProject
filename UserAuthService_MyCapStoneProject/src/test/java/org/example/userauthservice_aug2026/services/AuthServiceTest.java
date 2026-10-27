package org.example.userauthservice_aug2026.services;

import org.antlr.v4.runtime.misc.Pair;
import org.example.userauthservice_aug2026.clients.KafkaProducerClient;
import org.example.userauthservice_aug2026.exceptions.InvalidTokenException;
import org.example.userauthservice_aug2026.exceptions.PasswordMismatchException;
import org.example.userauthservice_aug2026.exceptions.UserAlreadyExistException;
import org.example.userauthservice_aug2026.models.Role;
import org.example.userauthservice_aug2026.models.User;
import org.example.userauthservice_aug2026.repos.PasswordResetTokenRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
class AuthServiceTest {

    @Autowired
    private IAuthService authService;

    @Autowired
    private PasswordResetTokenRepo passwordResetTokenRepo;

    @MockBean
    private KafkaProducerClient kafkaProducerClient;

    @Test
    void signup_PublishesWelcomeEmail_AndRejectsDuplicateEmail() {
        User user = authService.signup("Asha", "asha@test.local", "pass@123", "9000000001");

        assertNotNull(user.getId());
        assertNotEquals("pass@123", user.getPassword(), "password must be stored hashed");
        verify(kafkaProducerClient).sendMessage(eq(AuthService.SIGNUP_TOPIC), contains("asha@test.local"));

        assertThrows(UserAlreadyExistException.class,
                () -> authService.signup("Asha 2", "asha@test.local", "x", "9000000002"));
    }

    @Test
    void signup_WithAdminEmail_GetsAdminRole() {
        User user = authService.signup("Admin", "admin@test.local", "pass@123", "9000000003");
        List<String> roles = user.getRoles().stream().map(Role::getValue).toList();
        assertTrue(roles.contains("ADMIN"));
        assertTrue(roles.contains("NON_ADMIN"));
    }

    @Test
    void login_ThenValidate_ThenLogout_InvalidatesToken() {
        authService.signup("Ravi", "ravi@test.local", "secret", "9000000004");

        Pair<User, String> loginResult = authService.login("ravi@test.local", "secret");
        String token = loginResult.b;

        assertTrue(authService.validateToken(token));
        assertEquals("ravi@test.local", authService.getUserForToken(token).getEmail());

        authService.logout(token);
        verify(kafkaProducerClient).sendMessage(eq(AuthService.SESSION_REVOKED_TOPIC),
                argThat(hash -> hash.matches("[0-9a-f]{64}") && !hash.contains(token)));
        assertFalse(authService.validateToken(token));
        assertThrows(InvalidTokenException.class, () -> authService.getUserForToken(token));
    }

    @Test
    void twoLogins_GiveDistinctTokens() {
        authService.signup("Meena", "meena@test.local", "secret", "9000000005");
        String first = authService.login("meena@test.local", "secret").b;
        String second = authService.login("meena@test.local", "secret").b;
        assertNotEquals(first, second);
    }

    @Test
    void login_WithWrongPassword_Throws() {
        authService.signup("Kiran", "kiran@test.local", "right", "9000000006");
        assertThrows(PasswordMismatchException.class, () -> authService.login("kiran@test.local", "wrong"));
    }

    @Test
    void passwordReset_ChangesPassword_AndSignsOutExistingSessions() {
        authService.signup("Divya", "divya@test.local", "old-pass", "9000000007");
        String oldToken = authService.login("divya@test.local", "old-pass").b;

        authService.requestPasswordReset("divya@test.local");
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaProducerClient).sendMessage(eq(AuthService.PASSWORD_RESET_TOPIC), message.capture());
        String resetToken = passwordResetTokenRepo.findAll().stream()
                .filter(t -> t.getUser().getEmail().equals("divya@test.local"))
                .findFirst().orElseThrow().getToken();
        assertTrue(message.getValue().contains(resetToken));

        authService.resetPassword(resetToken, "new-pass");
        verify(kafkaProducerClient).sendMessage(eq(AuthService.SESSION_REVOKED_TOPIC), anyString());

        assertFalse(authService.validateToken(oldToken));
        assertThrows(PasswordMismatchException.class, () -> authService.login("divya@test.local", "old-pass"));
        assertNotNull(authService.login("divya@test.local", "new-pass").b);
        // reset tokens are single use
        assertThrows(InvalidTokenException.class, () -> authService.resetPassword(resetToken, "again"));
    }

    @Test
    void passwordReset_ForUnknownEmail_IsSilent() {
        authService.requestPasswordReset("nobody@test.local");
        verify(kafkaProducerClient, never()).sendMessage(eq(AuthService.PASSWORD_RESET_TOPIC), anyString());
    }

    private static String contains(String s) {
        return argThat(arg -> arg != null && arg.contains(s));
    }
}
