package com.flux.user_management_service.security;

import com.flux.user_management_service.user.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public void sendVerificationEmail(User user) {

        String recipientAddress = user.getEmail();
        String subject = "Verify Your Email";

        String confirmationUrl = frontendUrl + "/verify?token=" + user.getVerificationToken();

        String message = """
            <html>
            <body style="font-family: Arial, sans-serif; background:#f4f6f9; padding:40px;">
            
            <div style="max-width:600px; margin:auto; background:white; padding:30px; border-radius:12px; box-shadow:0 4px 12px rgba(0,0,0,0.1);">
            
            <h2 style="color:#2c3e50;">Welcome to Flux 🚀</h2>
            
            <p>Hello <b>%s</b>,</p>
            
            <p>Thank you for signing up! Please verify your email address by clicking the button below.</p>
            
            <div style="text-align:center; margin:30px 0;">
                <a href="%s"
                   style="background:#4CAF50;
                          color:white;
                          padding:14px 28px;
                          text-decoration:none;
                          border-radius:8px;
                          font-weight:bold;
                          display:inline-block;">
                   Verify Email
                </a>
            </div>
            
            <p style="color:#666;">
            This verification link will expire in <b>24 hours</b>.
            </p>
            
            <hr style="margin:30px 0;">
            
            <p style="font-size:12px; color:#888;">
            If you didn’t create this account, you can safely ignore this email.
            </p>
            
            </div>
            
            </body>
            </html>
            """.formatted(user.getFirstname(), confirmationUrl);

        try{
            sendEmail(recipientAddress, subject, message);
        }
        catch (Exception e) {
            notifyError(recipientAddress, e.getMessage());
        }
    }

    public void sendLoginNotification(User user) {

        String recipientAddress = user.getEmail();
        String subject = "New Login Detected";

        String message = """
            <html>
            <body style="font-family: Arial, sans-serif; background:#f4f6f9; padding:40px;">
            
            <div style="max-width:600px; margin:auto; background:white; padding:30px; border-radius:12px; box-shadow:0 4px 12px rgba(0,0,0,0.1);">
            
            <h2 style="color:#2c3e50;">🔐 New Login Detected</h2>
            
            <p>Hello <b>%s</b>,</p>
            
            <p>A new login was detected on your account.</p>
            
            <div style="background:#f8f9fa; padding:15px; border-radius:8px; margin:20px 0;">
                <b>Time:</b> %s
            </div>
            
            <p>If this was you, you can ignore this email.</p>
            
            <p style="color:#e74c3c;">
            If you did NOT log in, please reset your password immediately.
            </p>
            
            <hr style="margin:30px 0;">
            
            <p style="font-size:12px; color:#888;">
            Flux Security System
            </p>
            
            </div>
            
            </body>
            </html>
            """.formatted(user.getFirstname(), java.time.LocalDateTime.now());

        try{
            sendEmail(recipientAddress, subject, message);
        }
        catch (Exception e) {
            notifyError(recipientAddress, e.getMessage());
        }
    }

    public void sendRegistrationSuccessEmail(User user) {

        String recipientAddress = user.getEmail();
        String subject = "Registration Successful";

        String message = """
            <html>
            <body style="font-family: Arial, sans-serif; background:#f4f6f9; padding:40px;">
            
            <div style="max-width:600px; margin:auto; background:white; padding:30px; border-radius:12px; box-shadow:0 4px 12px rgba(0,0,0,0.1);">
            
            <h2 style="color:#27ae60;">✅ Email Verified</h2>
            
            <p>Hello <b>%s</b>,</p>
            
            <p>Your email has been successfully verified.</p>
            
            <p>You can now log in and start using your account.</p>
            
            <div style="text-align:center; margin:30px 0;">
                <a href="%s"
                   style="background:#3498db;
                          color:white;
                          padding:14px 28px;
                          text-decoration:none;
                          border-radius:8px;
                          font-weight:bold;
                          display:inline-block;">
                   Go to Dashboard
                </a>
            </div>
            
            <hr style="margin:30px 0;">
            
            <p style="font-size:12px; color:#888;">
            Thank you for joining Flux 🚀
            </p>
            
            </div>
            
            </body>
            </html>
            """.formatted(user.getFirstname(), frontendUrl);

        try{
            sendEmail(recipientAddress, subject, message);
        }
        catch (Exception e) {
            notifyError(recipientAddress, e.getMessage());
        }
    }

    public void sendPasswordChangeNotification(String name, String email) {

        String subject = "Password Changed Successfully";

        String message = """
            <html>
            <body style="font-family: Arial, sans-serif; background:#f4f6f9; padding:40px;">
            
            <div style="max-width:600px; margin:auto; background:white; padding:30px; border-radius:12px; box-shadow:0 4px 12px rgba(0,0,0,0.1);">
            
            <h2 style="color:#2c3e50;">🔒 Password Changed</h2>
            
            <p>Hello <b>%s</b>,</p>
            
            <p>Your account password has been successfully changed.</p>
            
            <div style="background:#f8f9fa; padding:15px; border-radius:8px; margin:20px 0;">
                <b>Time:</b> %s
            </div>
            
            <p>If you made this change, you can safely ignore this email.</p>
            
            <p style="color:#e74c3c;">
            If you did NOT change your password, please contact our support team immediately.
            </p>
            
            <hr style="margin:30px 0;">
            
            <p style="font-size:12px; color:#888;">
            Flux Security System
            </p>
            
            </div>
            
            </body>
            </html>
            """.formatted(name, java.time.LocalDateTime.now());

        try{
            sendEmail(email, subject, message);
        }
        catch (Exception e) {
            notifyError(email, e.getMessage());
        }
    }

    private void notifyError(String email, String message) {
        log.error("Failed to send email to {} due to {}", email, message);
    }

//    private void sendEmail(String to, String subject, String text) {
//        SimpleMailMessage email = new SimpleMailMessage();
//        email.setFrom(senderEmail, "Flux");
//        email.setTo(to);
//        email.setSubject(subject);
//        email.setText(text);
//        mailSender.send(email);
//    }

    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(senderEmail, "Flux security");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);

            javaMailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send email to {}", to, e);
        }
    }
}