package com.mahavircourier.controller.admin;

import com.mahavircourier.service.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

final class AdminAuth {

    private AdminAuth() {
    }

    static CustomUserDetails requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            throw new IllegalStateException("Admin principal required");
        }
        return details;
    }
}
