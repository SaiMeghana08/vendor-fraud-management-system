package com.vendorfraud.user;

import com.vendorfraud.security.CurrentUserService;
import com.vendorfraud.user.dto.LoginRequest;
import com.vendorfraud.user.dto.LoginResponse;
import com.vendorfraud.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication. There is intentionally no POST /register and no
 * endpoint that grants a role.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserDirectoryService userDirectoryService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, UserDirectoryService userDirectoryService,
                          CurrentUserService currentUserService) {
        this.authService = authService;
        this.userDirectoryService = userDirectoryService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** The caller's own profile, derived from the token. */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me() {
        return ResponseEntity.ok(userDirectoryService.me(currentUserService.currentUserId()));
    }
}
