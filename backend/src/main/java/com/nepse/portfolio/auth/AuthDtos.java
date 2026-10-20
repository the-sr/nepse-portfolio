package com.nepse.portfolio.auth;

import com.nepse.portfolio.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 120) String name
    ) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record UserDto(Long id, String email, String name, String role) {
        public static UserDto from(User user) {
            return new UserDto(user.getId(), user.getEmail(), user.getName(), user.getRole().name());
        }
    }

    public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserDto user) {
    }
}
