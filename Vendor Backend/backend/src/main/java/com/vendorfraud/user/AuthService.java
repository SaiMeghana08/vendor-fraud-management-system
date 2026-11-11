package com.vendorfraud.user;

import com.vendorfraud.audit.AuditAction;
import com.vendorfraud.audit.AuditService;
import com.vendorfraud.security.AppUserPrincipal;
import com.vendorfraud.security.JwtService;
import com.vendorfraud.user.dto.LoginRequest;
import com.vendorfraud.user.dto.LoginResponse;
import com.vendorfraud.user.dto.UserProfileResponse;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login. Passwords are verified against a BCrypt hash; nothing else in
 * the application reads the hash.
 *
 * <p>A deactivated identity cannot log in, and the failure is recorded
 * distinctly from a wrong password so that an auditor can tell the two
 * apart. The response to the caller is deliberately identical in both
 * cases.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<User> maybeUser = userRepository.findByUsername(request.username());

        if (maybeUser.isEmpty()) {
            auditService.recordSecurityEvent(null, request.username(),
                    AuditAction.LOGIN_FAILED, "Unknown username");
            throw new BadCredentialsException("Invalid username or password");
        }
        User user = maybeUser.get();

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.recordSecurityEvent(user.getId(), user.getUsername(),
                    AuditAction.LOGIN_FAILED, "Incorrect password");
            throw new BadCredentialsException("Invalid username or password");
        }

        if (!user.isActive()) {
            auditService.recordSecurityEvent(user.getId(), user.getUsername(),
                    AuditAction.LOGIN_BLOCKED_INACTIVE,
                    "Login attempt by a deactivated (soft-deleted) user");
            log.warn("Rejected login for deactivated user {}", user.getUsername());
            throw new DisabledException("Invalid username or password");
        }

        AppUserPrincipal principal = new AppUserPrincipal(user);
        String token = jwtService.issueToken(principal);

        auditService.recordSecurityEvent(user.getId(), user.getUsername(),
                AuditAction.LOGIN_SUCCEEDED, "Access token issued");

        log.info("User {} logged in with roles {}", user.getUsername(),
                principal.getAuthorities());
        return new LoginResponse(token, "Bearer", jwtService.expiryOf(token),
                UserProfileResponse.from(user),
                principal.getAuthorities().stream().map(a -> a.getAuthority()).sorted().toList());
    }
}
