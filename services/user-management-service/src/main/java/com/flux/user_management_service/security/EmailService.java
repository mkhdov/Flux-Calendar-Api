package com.flux.user_management_service.security;

import com.flux.user_management_service.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public void sendVerificationEmail(User user) {
        String recipientAddress = user.getEmail();
        String subject = "Email Verification";
        String confirmationUrl = frontendUrl + "/verify?token=" + user.getVerificationToken();

        String message = "Dear " + user.getFirstname() + ",\n\n" +
                "Please click the link below to verify your email address:\n" +
                confirmationUrl + "\n\n" +
                "This link will expire in 24 hours.\n\n" +
                "Best regards,\nYour Application Team";

        sendEmail(recipientAddress, subject, message);
    }

    public void sendLoginNotification(User user) {
        String recipientAddress = user.getEmail();
        String subject = "New Login Detected";
        String message = "Dear " + user.getFirstname() + ",\n\n" +
                "A new login was detected for your account at " + java.time.LocalDateTime.now() + ".\n" +
                "If this wasn't you, please reset your password immediately.\n\n" +
                "Best regards,\nYour Application Team";

        sendEmail(recipientAddress, subject, message);
    }

    public void sendRegistrationSuccessEmail(User user) {
        String recipientAddress = user.getEmail();
        String subject = "Registration Successful";
        String message = "Dear " + user.getFirstname() + ",\n\n" +
                "Your email has been successfully verified. You can now log in to your account.\n\n" +
                "Best regards,\nYour Application Team";

        sendEmail(recipientAddress, subject, message);
    }

    private void sendEmail(String to, String subject, String text) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(to);
        email.setSubject(subject);
        email.setText(text);
        mailSender.send(email);
    }
}