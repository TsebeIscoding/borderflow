package com.borderflow.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VehicleAvailabilityRepository extends JpaRepository<VehicleAvailability, UUID> {
}
