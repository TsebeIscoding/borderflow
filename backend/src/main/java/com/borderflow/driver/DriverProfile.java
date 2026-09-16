package com.borderflow.driver;

import jakarta.persistence.*;
import java.util.UUID;

/** Master fragment. Same lockdown pattern as ContainerMaster/VehicleProfile. */
@Entity
@Table(name = "driver_profile")
public class DriverProfile {

    @Id
    @Column(name = "driver_id")
    private UUID driverId;

    private String name;

    @Column(name = "license_number")
    private String licenseNumber;

    private String phone;

    protected DriverProfile() {
        // JPA
    }

    public UUID getDriverId() {
        return driverId;
    }

    public String getName() {
        return name;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public String getPhone() {
        return phone;
    }
}
