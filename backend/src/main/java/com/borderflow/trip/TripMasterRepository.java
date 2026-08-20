package com.borderflow.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TripMasterRepository extends JpaRepository<TripMaster, UUID> {
}
