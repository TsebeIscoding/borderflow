package com.borderflow.handover;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface HandoverRepository extends JpaRepository<Handover, UUID> {
}
