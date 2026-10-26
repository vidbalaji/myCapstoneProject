package org.example.notificationservice_aug2026.controllers;

import org.example.notificationservice_aug2026.services.EmailSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// Internal endpoint (not routed by the API Gateway): shows recently sent notifications
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private EmailSender emailSender;

    @GetMapping("/recent")
    public List<Map<String, String>> recent(@RequestParam(required = false) String to) {
        return emailSender.recentNotifications().stream()
                .filter(n -> to == null || to.equalsIgnoreCase(n.get("to")))
                .toList();
    }
}
