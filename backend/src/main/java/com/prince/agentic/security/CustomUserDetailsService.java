package com.prince.agentic.security;

import com.prince.agentic.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Loads a user by email for the login (username/password) authentication path.
 * Presence of this bean, plus a {@code PasswordEncoder}, makes Spring Boot back off its
 * default in-memory user — there is no generated password and no default account.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(email)
                .map(SecurityUser::new)
                // Generic message — never reveals whether the account exists.
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
    }
}
