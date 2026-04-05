package dev.trailhead.exception;

// Unchecked exceptions bubble up automatically at runtime until something catches them.
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("An account with email '" + email + "' already exists");
    }
}
