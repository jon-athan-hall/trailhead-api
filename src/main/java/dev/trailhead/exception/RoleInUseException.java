package dev.trailhead.exception;

public class RoleInUseException extends RuntimeException {

    public RoleInUseException(Long roleId) {
        super("Role with id " + roleId + " cannot be deleted because it is assigned to one or more users");
    }
}
