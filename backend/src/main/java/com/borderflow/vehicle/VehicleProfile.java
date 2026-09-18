package com.borderflow.vehicle;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Master fragment. Single-leader, same lockdown pattern as ContainerMaster -- see its javadoc. */
@Entity
@Table(name = "vehicle_profile")
public class VehicleProfile {

    @Id
    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "registration_number")
    private String registrationNumber;

    private BigDecimal capacity;

    protected VehicleProfile() {
        // JPA
    }

    public VehicleProfile(UUID vehicleId, String registrationNumber, BigDecimal capacity) {
        this.vehicleId = vehicleId;
        this.registrationNumber = registrationNumber;
        this.capacity = capacity;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public BigDecimal getCapacity() {
        return capacity;
    }
}
