package com.vendorfraud.user;

import com.vendorfraud.user.dto.UserProfileResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only directory used by the reassignment dialog and the
 * Administrator's exception views. This is not a user-management
 * module: nothing here creates, edits or assigns roles.
 */
@Service
public class UserDirectoryService {

    private final UserRepository userRepository;

    public UserDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> listActiveApprovers() {
        return userRepository.findActiveByRole(RoleName.APPROVER).stream()
                .map(UserProfileResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(Long userId) {
        return userRepository.findById(userId)
                .map(UserProfileResponse::from)
                .orElseThrow(() -> new com.vendorfraud.common.exception.ResourceNotFoundException(
                        "User", userId));
    }
}
