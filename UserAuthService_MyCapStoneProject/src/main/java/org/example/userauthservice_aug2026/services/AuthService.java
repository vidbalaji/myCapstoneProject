package org.example.userauthservice_aug2026.services;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.MacAlgorithm;
import org.antlr.v4.runtime.misc.Pair;
import org.apache.kafka.clients.KafkaClient;
import org.apache.kafka.common.network.KafkaChannel;
import org.example.userauthservice_aug2026.clients.KafkaProducerClient;
import org.example.userauthservice_aug2026.dtos.EmailDto;
import org.example.userauthservice_aug2026.exceptions.PasswordMismatchException;
import org.example.userauthservice_aug2026.exceptions.UserAlreadyExistException;
import org.example.userauthservice_aug2026.exceptions.UserNotRegisteredException;
import org.example.userauthservice_aug2026.models.Role;
import org.example.userauthservice_aug2026.models.Status;
import org.example.userauthservice_aug2026.models.User;
import org.example.userauthservice_aug2026.models.UserSession;
import org.example.userauthservice_aug2026.repos.RoleRepo;
import org.example.userauthservice_aug2026.repos.SessionRepo;
import org.example.userauthservice_aug2026.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.swing.text.html.Option;
import java.util.*;

@Service
public class AuthService implements IAuthService {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private SessionRepo sessionRepo;

    @Autowired
    private SecretKey secretKey;

    @Autowired
    private KafkaProducerClient kafkaProducerClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public User signup(String name, String email, String password, String phoneNumber) {
        Optional<User> userOptional = userRepo.findByEmail(email);
        if (userOptional.isPresent()) {
            throw new UserAlreadyExistException("User with email "+email+" already exists");
        }

        userOptional = userRepo.findByPhoneNumber(phoneNumber);
        if(userOptional.isPresent()) {
            throw new UserAlreadyExistException("User with phoneNumber "+phoneNumber+" already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPhoneNumber(phoneNumber);
        user.setPassword(bCryptPasswordEncoder.encode(password));  //This will be encoded by Anurag Khanna

        Role role;
        Optional<Role> optionalRole = roleRepo.findByValue("NON_ADMIN");
        if(optionalRole.isEmpty()) {
            role = new Role();
            role.setValue("NON_ADMIN");
            roleRepo.save(role);
        } else {
            role = optionalRole.get();
        }

        List<Role> roleList = new ArrayList<>();
        roleList.add(role);
        user.setRoles(roleList);

        /// Putting a message into Kafka
        EmailDto emailDto  = new EmailDto();
        emailDto.setTo(email);
        emailDto.setFrom("anuragonhiring@gmail.com");
        emailDto.setSubject("Welcome to Scaler");
        emailDto.setBody("Hey "+name+", Have a good learning experience");
        try {
            String message = objectMapper.writeValueAsString(emailDto);
            kafkaProducerClient.sendMessage("signup", message);
        }catch (JsonProcessingException exception) {
            throw new RuntimeException(exception.getMessage());
        }
        //////////////////////////////

        return userRepo.save(user);
    }

    @Override
    public Pair<User,String> login(String email, String password) {
        Optional<User> userOptional = userRepo.findByEmail(email);
        if (userOptional.isEmpty()) {
           throw new UserNotRegisteredException("pLease signup first !!! ");
        }

        User user = userOptional.get();
        //if (!user.getPassword().equals(password)) {
         if (!bCryptPasswordEncoder.matches(password, user.getPassword()))   {
            throw new PasswordMismatchException("Please type correct password");
        }

         // Generate JWT
         Map<String,Object> claims = new HashMap<>();
         claims.put("user_id",user.getId());
         claims.put("issuer","scaler");
         Long currentTime = System.currentTimeMillis();
         claims.put("iat",currentTime);
         claims.put("exp",currentTime+100000);
         List<String> roles = new ArrayList<>();
         for(Role role : user.getRoles()) {
             roles.add(role.getValue());
         }

         claims.put("access",roles);
         String token = Jwts.builder().claims(claims).signWith(secretKey).compact();

        UserSession userSession = new UserSession();
        userSession.setToken(token);
        userSession.setUser(user);
        sessionRepo.save(userSession);

        return new Pair<>(user,token);
    }


    //Validation
    public Boolean validateToken(String token /*,Long user_id*/) {
        Optional<UserSession> optionalUserSession = sessionRepo.findByToken(token);

        if(optionalUserSession.isEmpty()) return false;

        JwtParser jwtParser = Jwts.parser().verifyWith(secretKey).build();
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();

        Long expiry = (Long)claims.get("exp");
        Long currentTime = System.currentTimeMillis();

        System.out.println("expiry = "+expiry);
        System.out.println("currentTime = "+currentTime);
        if(currentTime > expiry) {
            UserSession userSession = optionalUserSession.get();
            //userSession.setStatus(Status.INACTIVE);
            //sessionRepo.save(userSession);
            sessionRepo.deleteById(optionalUserSession.get().getId());

            System.out.println("Token has expired");
            return false;
        }

        // Add a comment for user_id

        return true;
    }
}
