package com.borderflow.deliverypoint;

import com.borderflow.common.DeliveryPointNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Delivery point CRUD: both roles read, OPERATOR plus origin-only for create and delete. */
@RestController
@RequestMapping("/api/delivery-points")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class DeliveryPointController {

    private final DeliveryPointRepository deliveryPointRepository;
    private final DeliveryPointService service;

    public DeliveryPointController(DeliveryPointRepository deliveryPointRepository, DeliveryPointService service) {
        this.deliveryPointRepository = deliveryPointRepository;
        this.service = service;
    }

    @GetMapping
    public List<DeliveryPointSummaryResponse> listDeliveryPoints() {
        return deliveryPointRepository.findAllByOrderByNameAsc().stream()
                .map(DeliveryPointSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{deliveryPointId}")
    public DeliveryPointSummaryResponse getDeliveryPoint(@PathVariable UUID deliveryPointId) {
        return deliveryPointRepository.findById(deliveryPointId)
                .map(DeliveryPointSummaryResponse::from)
                .orElseThrow(() -> new DeliveryPointNotFoundException(deliveryPointId));
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping
    public ResponseEntity<DeliveryPointSummaryResponse> createDeliveryPoint(@Valid @RequestBody DeliveryPointCreateRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @DeleteMapping("/{deliveryPointId}")
    public ResponseEntity<Void> deleteDeliveryPoint(@PathVariable UUID deliveryPointId) {
        service.delete(deliveryPointId);
        return ResponseEntity.noContent().build();
    }
}
