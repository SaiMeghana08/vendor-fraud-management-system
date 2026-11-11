package com.vendorfraud.security;

import com.vendorfraud.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A valid signature is not enough: a soft-deleted user must stop being
 * able to act immediately, without waiting for the token to expire.
 */
@Component
public class ActiveUserGuard {

    private final UserRepository userRepository;

    public ActiveUserGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public boolean isStillActive(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId).map(u -> u.isActive()).orElse(false);
    }
}
