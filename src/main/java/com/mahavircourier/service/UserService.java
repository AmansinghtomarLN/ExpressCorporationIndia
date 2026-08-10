package com.mahavircourier.service;

import com.mahavircourier.dao.UserDao;
import com.mahavircourier.dto.SignupForm;
import com.mahavircourier.model.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class UserService implements UserDetailsService {

    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "ADMIN", "STAFF");

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserDao userDao, PasswordEncoder passwordEncoder) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userDao.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for " + email));
        return new CustomUserDetails(user);
    }

    public boolean emailTaken(String email) {
        return userDao.existsByEmail(email);
    }

    public Optional<User> findById(Long id) {
        return userDao.findById(id);
    }

    public List<User> listUsers() {
        return userDao.findAll();
    }

    public List<User> searchUsers(String query) {
        return userDao.search(query);
    }

    /**
     * Registers a new customer account. Password is hashed with BCrypt before
     * persisting - the raw password never touches the database.
     */
    public User registerCustomer(SignupForm form) {
        User user = new User();
        user.setFullName(form.getFullName().trim());
        user.setEmail(form.getEmail().trim().toLowerCase());
        user.setPhone(form.getPhone().trim());
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setRole("CUSTOMER");
        user.setEnabled(true);
        Long id = userDao.save(user);
        user.setId(id);
        return user;
    }

    @Transactional
    public User createUser(String fullName, String email, String phone, String password, String role) {
        if (userDao.existsByEmail(email.trim().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered");
        }
        String normalizedRole = normalizeRole(role);
        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setPhone(phone.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(normalizedRole);
        user.setEnabled(true);
        Long id = userDao.save(user);
        user.setId(id);
        return user;
    }

    @Transactional
    public void setEnabled(Long userId, boolean enabled) {
        userDao.updateEnabled(userId, enabled);
    }

    @Transactional
    public void setRole(Long userId, String role) {
        userDao.updateRole(userId, normalizeRole(role));
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        userDao.updatePasswordHash(userId, passwordEncoder.encode(newPassword));
    }

    /**
     * Permanently deletes a user. Blocks deleting yourself or the last remaining ADMIN.
     */
    @Transactional
    public void deleteUser(Long userId, Long actingUserId) {
        User target = userDao.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (actingUserId != null && actingUserId.equals(userId)) {
            throw new IllegalArgumentException("You cannot delete your own account while logged in");
        }
        if ("ADMIN".equalsIgnoreCase(target.getRole()) && userDao.countByRole("ADMIN") <= 1) {
            throw new IllegalArgumentException("Cannot delete the last ADMIN account");
        }
        userDao.deleteById(userId);
    }

    @Transactional
    public void updateProfile(User user) {
        userDao.updateProfile(user);
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "CUSTOMER" : role.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid role: " + role);
        }
        return normalized;
    }
}
