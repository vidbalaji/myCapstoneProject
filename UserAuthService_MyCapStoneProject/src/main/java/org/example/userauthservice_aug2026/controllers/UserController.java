package org.example.userauthservice_aug2026.controllers;

import jakarta.ws.rs.Path;
import org.example.userauthservice_aug2026.dtos.UserDto;
import org.example.userauthservice_aug2026.models.Role;
import org.example.userauthservice_aug2026.models.User;
import org.example.userauthservice_aug2026.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private UserDto from(User user) {
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setName(user.getName());
        userDto.setEmail(user.getEmail());
        List<String> roleValues = new ArrayList<>();
        for(Role role : user.getRoles()) {
            roleValues.add(role.getValue());
        }
        userDto.setRoles(roleValues);
        return userDto;
    }
}
