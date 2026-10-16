package com.borderflow.common;

import java.util.UUID;

public class DeliveryPointNotFoundException extends RuntimeException {
    public DeliveryPointNotFoundException(UUID deliveryPointId) {
        super("No delivery point found with id " + deliveryPointId);
    }
}
