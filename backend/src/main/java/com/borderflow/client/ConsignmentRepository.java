package com.borderflow.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface ConsignmentRepository extends JpaRepository<Consignment, UUID> {
    List<Consignment> findByClientId(UUID clientId);

    /**
     * Next number from the Depot-only reference sequence (V10). Only
     * ever called at Depot, because creation is origin-only.
     */
    @Query(value = "select nextval('consignment_reference_seq')", nativeQuery = true)
    long nextReferenceSequence();
}
