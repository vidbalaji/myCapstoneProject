package org.example.userauthservice_aug2026.services;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.antlr.v4.runtime.misc.Pair;
import org.example.userauthservice_aug2026.clients.KafkaProducerClient;
import org.example.userauthservice_aug2026.dtos.EmailDto;
import org.example.userauthservice_aug2026.exceptions.InvalidTokenException;
import org.example.userauthservice_aug2026.exceptions.PasswordMismatchException;
import org.example.userauthservice_aug2026.exceptions.UserAlreadyExistException;
import org.example.userauthservice_aug2026.exceptions.UserNotRegisteredException;
import org.example.userauthservice_aug2026.models.PasswordResetToken;
import org.example.userauthservice_aug2026.models.Role;
import org.example.userauthservice_aug2026.models.User;
import org.example.userauthservice_aug2026.models.UserSession;
import org.example.userauthservice_aug2026.repos.PasswordResetTokenRepo;
import org.example.userauthservice_aug2026.repos.RoleRepo;
import org.example.userauthservice_aug2026.repos.SessionRepo;
import org.example.userauthservice_aug2026.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class AuthService implements IAuthService {

    public static final String SIGNUP_TOPIC = "signup";
    public static final String PASSWORD_RESET_TOPIC = "password-reset";
    // Tells the API Gateway to drop its cached validation of a token (sent as a SHA-256 hash, never the token)
    public static final String SESSION_REVOKED_TOPIC = "session-revoked";

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private SessionRepo sessionRepo;

    @Autowired
    private PasswordResetTokenRepo passwordResetTokenRepo;

    @Autowired
    private SecretKey secretKey;

    @Autowired
    private KafkaProducerClient kafkaProducerClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${jwt.expiry-minutes}")
    private Long jwtExpiryMinutes;

    @Value("${app.email.from}")
    private String emailFrom;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.password-reset.expiry-minutes}")
    private Long passwordResetExpiryMinutes;

    @Value("${app.admin-emails}")
    private Set<String> adminEmails;

    @Override
    public User signup(String name, String email, String password, String phoneNumber) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Email and password are required");
        }

        Optional<User> userOptional = userRepo.findByEmail(email);
        if (userOptional.isPresent()) {
            throw new UserAlreadyExistException("User with email "+email+" already exists");
        }

        if (phoneNumber != null) {
            userOptional = userRepo.findByPhoneNumber(phoneNumber);
            if (userOptional.isPresent()) {
                throw new UserAlreadyExistException("User with phoneNumber " + phoneNumber + " already exists");
            }
        }

        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPhoneNumber(phoneNumber);
        user.setPassword(bCryptPasswordEncoder.encode(password));

        List<Role> roleList = new ArrayList<>();
        roleList.add(getOrCreateRole("NON_ADMIN"));
        if (adminEmails.contains(email)) {
            roleList.add(getOrCreateRole("ADMIN"));
        }
        user.setRoles(roleList);

        // Save first so a welcome email is never sent for a user that failed to persist
        User savedUser = userRepo.save(user);

        /// Putting a message into Kafka
        EmailDto emailDto  = new EmailDto();
        emailDto.setTo(email);
        emailDto.setFrom(emailFrom);
        emailDto.setSubject("Welcome to Capstone Shop");
        emailDto.setBody("Hey "+name+", welcome aboard! Happy shopping.");
        publish(SIGNUP_TOPIC, emailDto);

        return savedUser;
    }

    @Override
    public Pair<User,String> login(String email, String password) {
        Optional<User> userOptional = userRepo.findByEmail(email);
        if (userOptional.isEmpty()) {
           throw new UserNotRegisteredException("pLease signup first !!! ");
        }

        User user = userOptional.get();
         if (!bCryptPasswordEncoder.matches(password, user.getPassword()))   {
            throw new PasswordMismatchException("Please type correct password");
        }

         // Generate JWT
         Map<String,Object> claims = new HashMap<>();
         claims.put("user_id",user.getId());
         claims.put("email",user.getEmail());
         List<String> roles = new ArrayList<>();
         for(Role role : user.getRoles()) {
             roles.add(role.getValue());
         }
         claims.put("access",roles);

         long currentTime = System.currentTimeMillis();
         String token = Jwts.builder()
                 .claims(claims)
                 .id(UUID.randomUUID().toString())   // makes every token unique, even for 2 logins in the same second
                 .issuer("scaler")
                 .subject(String.valueOf(user.getId()))
                 .issuedAt(new Date(currentTime))
                 .expiration(new Date(currentTime + jwtExpiryMinutes * 60 * 1000))
                 .signWith(secretKey)
                 .compact();

        UserSession userSession = new UserSession();
        userSession.setToken(token);
        userSession.setUser(user);
        sessionRepo.save(userSession);

        return new Pair<>(user,token);
    }


    //Validation
    public Boolean validateToken(String token) {
        Optional<UserSession> optionalUserSession = sessionRepo.findByToken(token);

        // Session missing = user logged out (or token never issued by us)
        if(optionalUserSession.isEmpty()) return false;

        try {
            JwtParser jwtParser = Jwts.parser().verifyWith(secretKey).build();
            // parseSignedClaims verifies the signature and rejects expired tokens
            jwtParser.parseSignedClaims(token);
        } catch (JwtException exception) {
            // Expired or tampered token: the session is useless, clean it up
            sessionRepo.deleteById(optionalUserSession.get().getId());
            return false;
        }

        return true;
    }

    @Override
    public User getUserForToken(String token) {
        if (token == null || !validateToken(token)) {
            throw new InvalidTokenException("Token is invalid or has expired");
        }
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        return userRepo.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User for this token no longer exists"));
    }

    @Override
    public void logout(String token) {
        Optional<UserSession> optionalUserSession = sessionRepo.findByToken(token);
        if (optionalUserSession.isEmpty()) {
            throw new InvalidTokenException("No active session for this token");
        }
        sessionRepo.deleteById(optionalUserSession.get().getId());
        publishRevoked(token);
    }

    @Override
    public void requestPasswordReset(String email) {
        Optional<User> userOptional = userRepo.findByEmail(email);
        // Respond the same way whether or not the email exists, so this API can't be used to find accounts
        if (userOptional.isEmpty()) return;

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(userOptional.get());
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setExpiresAt(new Date(System.currentTimeMillis() + passwordResetExpiryMinutes * 60 * 1000));
        passwordResetTokenRepo.save(resetToken);

        EmailDto emailDto = new EmailDto();
        emailDto.setTo(email);
        emailDto.setFrom(emailFrom);
        emailDto.setSubject("Reset your Capstone Shop password");
        emailDto.setBody("Use this link to reset your password (valid for " + passwordResetExpiryMinutes
                + " minutes): " + frontendUrl + "/reset-password?token=" + resetToken.getToken());
        publish(PASSWORD_RESET_TOPIC, emailDto);
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required");
        }
        PasswordResetToken resetToken = passwordResetTokenRepo.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid password reset token"));
        if (resetToken.getExpiresAt().before(new Date())) {
            passwordResetTokenRepo.delete(resetToken);
            throw new InvalidTokenException("Password reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(bCryptPasswordEncoder.encode(newPassword));
        user.setLastUpdatedAt(new Date());
        userRepo.save(user);

        // Single use token, and every existing login is signed out
        passwordResetTokenRepo.delete(resetToken);
        for (UserSession session : sessionRepo.findByUser_Id(user.getId())) {
            publishRevoked(session.getToken());
        }
        sessionRepo.deleteByUser_Id(user.getId());
    }

    private Role getOrCreateRole(String value) {
        Optional<Role> optionalRole = roleRepo.findByValue(value);
        if (optionalRole.isPresent()) return optionalRole.get();
        Role role = new Role();
        role.setValue(value);
        return roleRepo.save(role);
    }

    private void publishRevoked(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            kafkaProducerClient.sendMessage(SESSION_REVOKED_TOPIC, HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void publish(String topic, EmailDto emailDto) {
        try {
            String message = objectMapper.writeValueAsString(emailDto);
            kafkaProducerClient.sendMessage(topic, message);
        }catch (JsonProcessingException exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }
}
