package dev.trailhead.exception;

public class RoleNameAlreadyExistsException extends RuntimeException {

    public RoleNameAlreadyExistsException(String name) {
        super("A role with name '" + name + "' already exists");
    }
}
