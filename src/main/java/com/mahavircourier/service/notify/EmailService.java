package com.mahavircourier.service.notify;

import com.mahavircourier.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;
    private final String mailUsername;

    public EmailService(JavaMailSender mailSender,
                        AppProperties appProperties,
                        @Value("${spring.mail.username:}") String mailUsername) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
        this.mailUsername = mailUsername;
    }

    public SendResult sendText(String to, String subject, String body) {
        if (!appProperties.getMail().isEnabled()) {
            return SendResult.skipped("Email disabled by configuration");
        }
        if (!StringUtils.hasText(to) || !to.contains("@") || to.endsWith("@notify.local")) {
            return SendResult.failed("Invalid or missing email recipient: " + to);
        }
        if (!isMailConfigured()) {
            return SendResult.failed("Mail credentials not configured (MAIL_USERNAME / MAIL_PASSWORD)");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(resolveFrom());
            message.setTo(to.trim());
            applyBcc(message, to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {} (bcc={})", to, resolveAlwaysBcc());
            return SendResult.ok();
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
            return SendResult.failed(ex.getMessage());
        }
    }

    public SendResult sendHtml(String to, String subject, String htmlBody) {
        if (!appProperties.getMail().isEnabled()) {
            return SendResult.skipped("Email disabled by configuration");
        }
        if (!StringUtils.hasText(to) || !to.contains("@")) {
            return SendResult.failed("Invalid or missing email recipient: " + to);
        }
        if (!isMailConfigured()) {
            return SendResult.failed("Mail credentials not configured (MAIL_USERNAME / MAIL_PASSWORD)");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(resolveFrom());
            helper.setTo(to.trim());
            String bcc = resolveAlwaysBcc();
            if (shouldBcc(to, bcc)) {
                helper.setBcc(bcc);
            }
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("HTML email sent to {} (bcc={})", to, bcc);
            return SendResult.ok();
        } catch (Exception ex) {
            log.error("Failed to send HTML email to {}: {}", to, ex.getMessage());
            return SendResult.failed(ex.getMessage());
        }
    }

    public boolean isMailConfigured() {
        return StringUtils.hasText(mailUsername);
    }

    private void applyBcc(SimpleMailMessage message, String to) {
        String bcc = resolveAlwaysBcc();
        if (shouldBcc(to, bcc)) {
            message.setBcc(bcc);
        }
    }

    private boolean shouldBcc(String to, String bcc) {
        if (!StringUtils.hasText(bcc) || !bcc.contains("@")) {
            return false;
        }
        // Avoid duplicate copy when the primary recipient is already the ops address
        return !bcc.trim().equalsIgnoreCase(to != null ? to.trim() : "");
    }

    private String resolveAlwaysBcc() {
        String configured = appProperties.getMail().getAlwaysBcc();
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        return "amansinghtomar2209@gmail.com";
    }

    private String resolveFrom() {
        if (StringUtils.hasText(appProperties.getMail().getFrom())) {
            return appProperties.getMail().getFrom();
        }
        if (StringUtils.hasText(mailUsername)) {
            return mailUsername;
        }
        return "noreply@expresscorpindia.com";
    }

    public record SendResult(boolean success, boolean skipped, String message) {
        public static SendResult ok() {
            return new SendResult(true, false, "SENT");
        }

        public static SendResult skipped(String reason) {
            return new SendResult(false, true, reason);
        }

        public static SendResult failed(String reason) {
            return new SendResult(false, false, reason != null ? reason : "FAILED");
        }

        public String status() {
            if (success) {
                return "SENT";
            }
            if (skipped) {
                return "SKIPPED";
            }
            return "FAILED";
        }
    }
}
