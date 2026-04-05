package dev.trailhead.exception;

// Unchecked exceptions bubble up automatically at runtime until something catches them.
public class TokenRefreshException extends RuntimeException {

    public TokenRefreshException(String message) {
        super(message);
    }
}
