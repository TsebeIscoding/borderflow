package com.borderflow.common;

import java.util.UUID;

public class ConsignmentNotFoundException extends RuntimeException {
    public ConsignmentNotFoundException(UUID consignmentId) {
        super("No consignment found with id " + consignmentId);
    }
}
