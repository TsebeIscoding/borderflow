package com.borderflow.milestone;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
    List<Milestone> findByTripIdOrderByOccurredAtAsc(UUID tripId);
}
