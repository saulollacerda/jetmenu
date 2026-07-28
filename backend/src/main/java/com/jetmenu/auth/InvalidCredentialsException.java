package com.jetmenu.auth;

/**
 * Thrown by the dev auth login when the email is unknown or the password does not match.
 * Mapped to {@code 401} by {@code GlobalExceptionHandler}.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email ou senha inválidos");
    }
}
