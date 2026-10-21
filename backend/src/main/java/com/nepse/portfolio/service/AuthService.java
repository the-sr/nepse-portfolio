package com.nepse.portfolio.service;

import com.nepse.portfolio.dto.auth.AuthDtos;

public interface AuthService {
    AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request);
    AuthDtos.AuthResponse login(AuthDtos.LoginRequest request);
    AuthDtos.AuthResponse refresh(String rawToken);
    void logout(String rawToken);
    AuthDtos.UserDto currentUser();
}
