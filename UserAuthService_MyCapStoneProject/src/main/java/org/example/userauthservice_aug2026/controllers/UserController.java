package org.example.userauthservice_aug2026.controllers;

import org.example.userauthservice_aug2026.dtos.UpdateProfileRequestDto;
import org.example.userauthservice_aug2026.dtos.UserDto;
import org.example.userauthservice_aug2026.models.Role;
import org.example.userauthservice_aug2026.models.User;
import org.example.userauthservice_aug2026.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;


    @GetMapping("/{id}")
    public UserDto getUserDetailsById(@PathVariable Long id) {
      User user = userService.getUserDetailsById(id);
      if(user == null) return null;
      return from(user);
    }

    // X-User-Id is set by the API Gateway after it has validated the caller's token
    @GetMapping("/me")
    public ResponseEntity<UserDto> getMyProfile(@RequestHeader("X-User-Id") Long userId) {
        User user = userService.getUserDetailsById(userId);
        if(user == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        return new ResponseEntity<>(from(user), HttpStatus.OK);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserDto> updateMyProfile(@RequestHeader("X-User-Id") Long userId,
                                                   @RequestBody UpdateProfileRequestDto updateProfileRequestDto) {
        User user = userService.updateProfile(userId,
                updateProfileRequestDto.getName(),
                updateProfileRequestDto.getPhoneNumber());
        return new ResponseEntity<>(from(user), HttpStatus.OK);
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
