package com.mahavircourier.config;

import com.mahavircourier.service.AuditService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class SecurityEventAuditor {

    private final AuditService auditService;

    public SecurityEventAuditor(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        auditService.log(null, auth.getName(), "LOGIN_SUCCESS", "AUTH", auth.getName(),
                "ip=" + clientIp() + "; ua=" + userAgent());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String principal = event.getAuthentication() != null
                ? String.valueOf(event.getAuthentication().getPrincipal())
                : "unknown";
        auditService.log(null, principal, "LOGIN_FAILURE", "AUTH", principal,
                "reason=" + event.getException().getMessage() + "; ip=" + clientIp());
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        String name = auth != null ? auth.getName() : "unknown";
        auditService.log(null, name, "LOGOUT", "AUTH", name, "ip=" + clientIp());
    }

    private String clientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "n/a";
            }
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception ex) {
            return "n/a";
        }
    }

    private String userAgent() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "n/a";
            }
            String ua = attrs.getRequest().getHeader("User-Agent");
            return ua != null ? ua : "n/a";
        } catch (Exception ex) {
            return "n/a";
        }
    }
}
