package com.mahavircourier.service.notify;

import com.mahavircourier.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final AppProperties appProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public SmsService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public EmailService.SendResult send(String phone, String message) {
        if (!appProperties.getSms().isEnabled()) {
            return EmailService.SendResult.skipped("SMS disabled by configuration");
        }
        String normalized = normalizePhone(phone);
        if (!StringUtils.hasText(normalized)) {
            return EmailService.SendResult.failed("Invalid SMS recipient phone");
        }
        if (!appProperties.getSms().getTwilio().isConfigured()) {
            return EmailService.SendResult.failed(
                    "Twilio not configured (TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN / TWILIO_FROM_NUMBER)");
        }

        try {
            var twilio = appProperties.getSms().getTwilio();
            String url = "https://api.twilio.com/2010-04-01/Accounts/"
                    + twilio.getAccountSid() + "/Messages.json";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String basic = twilio.getAccountSid() + ":" + twilio.getAuthToken();
            headers.set(HttpHeaders.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)));

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("To", normalized);
            form.add("From", twilio.getFromNumber());
            form.add("Body", message);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(form, headers), String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("SMS sent to {}", normalized);
                return EmailService.SendResult.ok();
            }
            return EmailService.SendResult.failed("Twilio HTTP " + response.getStatusCode());
        } catch (Exception ex) {
            log.error("Failed to send SMS to {}: {}", phone, ex.getMessage());
            return EmailService.SendResult.failed(ex.getMessage());
        }
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.length() == 10) {
            return "+91" + digits;
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;
        }
        return digits.startsWith("+") ? digits : "+" + digits;
    }
}
