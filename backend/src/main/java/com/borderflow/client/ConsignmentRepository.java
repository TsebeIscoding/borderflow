package com.borderflow.client;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface ConsignmentRepository extends JpaRepository<Consignment, UUID> {
    List<Consignment> findByClientId(UUID clientId);
}
