package com.borderflow.driver;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DriverAvailabilityRepository extends JpaRepository<DriverAvailability, UUID> {
}
