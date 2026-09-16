package com.borderflow.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VehicleProfileRepository extends JpaRepository<VehicleProfile, UUID> {
}
