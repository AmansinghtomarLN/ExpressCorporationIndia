package com.mahavircourier.service;

import com.mahavircourier.dao.UserDao;
import com.mahavircourier.dto.SignupForm;
import com.mahavircourier.model.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService {

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
}
