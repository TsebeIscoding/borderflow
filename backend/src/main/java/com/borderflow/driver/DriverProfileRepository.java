package com.borderflow.driver;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {
}
