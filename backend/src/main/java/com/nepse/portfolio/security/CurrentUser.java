package com.nepse.portfolio.security;

import com.nepse.portfolio.common.ApiException;
import com.nepse.portfolio.user.User;
import com.nepse.portfolio.user.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated user from the security context. Controllers use this
 * instead of trusting a userId supplied by the client.
 */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User entity() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw ApiException.unauthorized("Authentication required");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> ApiException.unauthorized("Account not found"));
    }

    public Long id() {
        return entity().getId();
    }
}
