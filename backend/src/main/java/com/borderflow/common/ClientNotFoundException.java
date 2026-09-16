package com.borderflow.common;

import java.util.UUID;

public class ClientNotFoundException extends RuntimeException {
    public ClientNotFoundException(UUID clientId) {
        super("No client found with id " + clientId);
    }
}
