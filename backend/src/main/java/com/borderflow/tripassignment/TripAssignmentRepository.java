package com.borderflow.tripassignment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TripAssignmentRepository extends JpaRepository<TripAssignment, UUID> {
    List<TripAssignment> findByTripIdOrderByOccurredAtAsc(UUID tripId);
}
