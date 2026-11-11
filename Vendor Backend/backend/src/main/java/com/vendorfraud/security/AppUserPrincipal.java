package com.vendorfraud.security;

import com.vendorfraud.user.Role;
import com.vendorfraud.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated identity. Every separation-of-duties check in the
 * service layer derives the acting user from this principal, never from
 * a field in the request body.
 */
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String fullName;
    private final String passwordHash;
    private final boolean active;
    private final List<GrantedAuthority> authorities;

    public AppUserPrincipal(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.fullName = user.getFullName();
        this.passwordHash = user.getPasswordHash();
        this.active = user.isActive();
        this.authorities = user.getRoles().stream()
                .map(Role::getName)
                .map(name -> (GrantedAuthority) new SimpleGrantedAuthority(name.authority()))
                .toList();
    }

    public AppUserPrincipal(Long id, String username, String fullName, List<String> authorities) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = "";
        this.active = true;
        this.authorities = authorities.stream()
                .map(a -> (GrantedAuthority) new SimpleGrantedAuthority(a))
                .toList();
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
