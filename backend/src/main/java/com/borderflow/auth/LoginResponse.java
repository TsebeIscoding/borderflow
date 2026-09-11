package com.borderflow.auth;

public record LoginResponse(
        String token,
        String username,
        Role role
) {
}
