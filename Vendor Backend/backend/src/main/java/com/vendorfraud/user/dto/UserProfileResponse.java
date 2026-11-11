package com.vendorfraud.user.dto;

import com.vendorfraud.user.Role;
import com.vendorfraud.user.User;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String username,
        String fullName,
        String email,
        boolean active,
        List<String> roles) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getFullName(),
                user.getEmail(), user.isActive(),
                user.getRoles().stream().map(Role::getName).map(Enum::name).sorted().toList());
    }
}
