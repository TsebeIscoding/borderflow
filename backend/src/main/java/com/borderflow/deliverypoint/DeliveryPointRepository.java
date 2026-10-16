package com.borderflow.deliverypoint;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DeliveryPointRepository extends JpaRepository<DeliveryPoint, UUID> {
    List<DeliveryPoint> findAllByOrderByNameAsc();
}
