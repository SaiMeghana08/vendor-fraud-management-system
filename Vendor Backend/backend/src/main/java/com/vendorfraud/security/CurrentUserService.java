package com.vendorfraud.security;

import com.vendorfraud.common.exception.ResourceNotFoundException;
import com.vendorfraud.user.User;
import com.vendorfraud.user.UserRepository;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for "who is acting". Service classes call this
 * rather than trusting any identifier that arrived in a request body.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<AppUserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public AppUserPrincipal requirePrincipal() {
        return currentPrincipal().orElseThrow(
                () -> new ResourceNotFoundException("No authenticated principal is present"));
    }

    public Long currentUserId() {
        return requirePrincipal().getId();
    }

    public String currentUsername() {
        return currentPrincipal().map(AppUserPrincipal::getUsername).orElse("system");
    }

    public User requireCurrentUser() {
        Long id = currentUserId();
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
