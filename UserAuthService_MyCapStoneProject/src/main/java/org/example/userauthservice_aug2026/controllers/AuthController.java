package org.example.userauthservice_aug2026.controllers;

import org.antlr.v4.runtime.misc.Pair;
import org.example.userauthservice_aug2026.dtos.*;
import org.example.userauthservice_aug2026.models.Role;
import org.example.userauthservice_aug2026.models.User;
import org.example.userauthservice_aug2026.services.IAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private IAuthService authService;

    //signup

    @PostMapping("/signup")
    public ResponseEntity<UserDto> signup(@RequestBody SignupRequestDto signupRequestDto) {
        User user = authService.signup(signupRequestDto.getName(),
                signupRequestDto.getEmail(),
                signupRequestDto.getPassword(),
                signupRequestDto.getPhoneNumber());

        UserDto userDto  = from(user);
        return new ResponseEntity<>(userDto, HttpStatus.CREATED);
    }

    //login
    @PostMapping("/login")
    public ResponseEntity<UserDto> login(@RequestBody LoginRequestDto loginRequestDto) {
        Pair<User,String> response = authService.login(loginRequestDto.getEmail(), loginRequestDto.getPassword());
        User user = response.a;
        String token = response.b;
        UserDto userDto = from(user);
        MultiValueMap<String,String> headers = new LinkedMultiValueMap<>();
        headers.add(HttpHeaders.SET_COOKIE,"auth_session_id=" + token + "; Path=/; HttpOnly");
        // Also returned as a header so non-browser clients can send it back as "Authorization: Bearer <token>"
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return new ResponseEntity<>(userDto,headers,HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequestDto logoutRequestDto) {
        authService.logout(logoutRequestDto.getToken());
        MultiValueMap<String,String> headers = new LinkedMultiValueMap<>();
        headers.add(HttpHeaders.SET_COOKIE,"auth_session_id=; Path=/; Max-Age=0");
        return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
    }

    @PostMapping("/validateToken")
    public Boolean validateToken(@RequestBody ValidateTokenRequestDto validateTokenRequestDto) {
         return authService.validateToken(validateTokenRequestDto.getToken());
    }

    // Used by the API Gateway: validates the token and tells it who the caller is
    @PostMapping("/validate")
    public UserDto validate(@RequestBody ValidateTokenRequestDto validateTokenRequestDto) {
        return from(authService.getUserForToken(validateTokenRequestDto.getToken()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequestDto forgotPasswordRequestDto) {
        authService.requestPasswordReset(forgotPasswordRequestDto.getEmail());
        return new ResponseEntity<>("If this email is registered, a reset link has been sent", HttpStatus.ACCEPTED);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequestDto resetPasswordRequestDto) {
        authService.resetPassword(resetPasswordRequestDto.getToken(), resetPasswordRequestDto.getNewPassword());
        return new ResponseEntity<>("Password updated. Please log in again.", HttpStatus.OK);
    }

    private UserDto from(User user) {
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setName(user.getName());
        userDto.setEmail(user.getEmail());
        userDto.setPhoneNumber(user.getPhoneNumber());
        List<String> roleValues = new ArrayList<>();
        for(Role role : user.getRoles()) {
            roleValues.add(role.getValue());
        }
        userDto.setRoles(roleValues);
        return userDto;
    }
}
