package com.prince.agentic.auth;

import com.prince.agentic.auth.dto.AuthResponse;
import com.prince.agentic.auth.dto.LoginRequest;
import com.prince.agentic.auth.dto.RegisterRequest;
import com.prince.agentic.auth.dto.UserResponse;
import com.prince.agentic.security.JwtService;
import com.prince.agentic.security.RoleNames;
import com.prince.agentic.security.SecurityUser;
import com.prince.agentic.user.Role;
import com.prince.agentic.user.RoleRepository;
import com.prince.agentic.user.User;
import com.prince.agentic.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication use cases: registration and login. This is the authoritative boundary —
 * it hashes passwords, assigns the safe default role, and issues tokens. It never trusts a
 * client-supplied role and never logs raw passwords, hashes, or tokens.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Register a new user with the default {@code ROLE_USER}. Duplicate identity is guarded
     * both by an application check and the DB unique constraint (race-safe).
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        Role userRole = roleRepository.findByName(RoleNames.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException(
                        "Default role " + RoleNames.ROLE_USER + " is not seeded"));

        User user = new User(email, passwordEncoder.encode(request.password()));
        user.addRole(userRole);

        try {
            User saved = userRepository.saveAndFlush(user);
            log.info("Registered new user id={}", saved.getId());
            return toUserResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Lost the race against a concurrent registration with the same email.
            throw new EmailAlreadyExistsException();
        }
    }

    /**
     * Authenticate credentials and issue a JWT. All failures collapse to a single generic
     * {@link InvalidCredentialsException} (no account enumeration).
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));

            SecurityUser principal = (SecurityUser) authentication.getPrincipal();
            Set<String> roles = principal.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());

            String token = jwtService.issueToken(principal.getUserId(), principal.getUsername(), roles);
            log.info("Successful login for user id={}", principal.getUserId());
            return AuthResponse.bearer(token, jwtService.getExpirationSeconds());
        } catch (AuthenticationException ex) {
            log.warn("Failed login attempt for email='{}'", email);
            throw new InvalidCredentialsException();
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private UserResponse toUserResponse(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return new UserResponse(user.getId(), user.getEmail(), roleNames, user.getCreatedAt());
    }
}
