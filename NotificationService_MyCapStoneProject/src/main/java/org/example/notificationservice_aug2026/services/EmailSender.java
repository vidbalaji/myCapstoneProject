package org.example.notificationservice_aug2026.services;

import org.example.notificationservice_aug2026.utils.EmailUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private static final int RECENT_LIMIT = 100;

    @Value("${notification.email.enabled}")
    private boolean emailEnabled;

    @Value("${notification.email.from}")
    private String from;

    @Value("${notification.smtp.host}")
    private String smtpHost;

    @Value("${notification.smtp.port}")
    private String smtpPort;

    @Value("${notification.smtp.username}")
    private String smtpUsername;

    @Value("${notification.smtp.password}")
    private String smtpPassword;

    // Last few notifications, for operators and the end-to-end smoke test
    private final Deque<Map<String, String>> recent = new ArrayDeque<>();

    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.warn("Skipping notification '{}' with no recipient", subject);
            return;
        }
        if (emailEnabled) {
            EmailUtil.sendEmail(buildSession(), from, to, subject, body);
            log.info("Email sent to {}: {}", to, subject);
        } else {
            log.info("[email disabled] to={} subject='{}' body='{}'", to, subject, body);
        }
        remember(to, subject, body);
    }

    public synchronized List<Map<String, String>> recentNotifications() {
        return new ArrayList<>(recent);
    }

    private synchronized void remember(String to, String subject, String body) {
        recent.addFirst(Map.of("to", to, "subject", subject, "body", body, "at", Instant.now().toString()));
        while (recent.size() > RECENT_LIMIT) recent.removeLast();
    }

    private Session buildSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost); //SMTP Host
        props.put("mail.smtp.port", smtpPort); //TLS Port
        props.put("mail.smtp.auth", "true"); //enable authentication
        props.put("mail.smtp.starttls.enable", "true"); //enable STARTTLS

        //create Authenticator object to pass in Session.getInstance argument
        Authenticator auth = new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        };
        return Session.getInstance(props, auth);
    }
}
