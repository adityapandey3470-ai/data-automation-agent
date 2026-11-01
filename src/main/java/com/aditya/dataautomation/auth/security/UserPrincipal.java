package com.aditya.dataautomation.auth.security;

import com.aditya.dataautomation.auth.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String name;
    private final String role;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id, String email, String password, String name, String role, boolean enabled) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role != null ? role : "ROLE_USER";
        this.enabled = enabled;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority(this.role));
    }

    public static UserPrincipal create(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getName(),
                user.getRole(),
                user.isEnabled()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
