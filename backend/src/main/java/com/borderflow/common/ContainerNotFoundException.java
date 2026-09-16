package com.borderflow.common;

import java.util.UUID;

public class ContainerNotFoundException extends RuntimeException {
    public ContainerNotFoundException(UUID containerId) {
        super("No container found with id " + containerId);
    }
}
