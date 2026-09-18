package com.borderflow.client;

import com.borderflow.common.ConsignmentNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Consignment CRUD, both roles for read, OPERATOR + origin-only for create/delete -- same reasoning as ClientController. */
@RestController
@RequestMapping("/api/consignments")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class ConsignmentController {

    private final ConsignmentRepository consignmentRepository;
    private final ConsignmentCreationService creationService;

    public ConsignmentController(ConsignmentRepository consignmentRepository, ConsignmentCreationService creationService) {
        this.consignmentRepository = consignmentRepository;
        this.creationService = creationService;
    }

    @GetMapping
    public List<ConsignmentSummaryResponse> listConsignments() {
        return consignmentRepository.findAll().stream()
                .map(ConsignmentSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{consignmentId}")
    public ConsignmentSummaryResponse getConsignment(@PathVariable UUID consignmentId) {
        return consignmentRepository.findById(consignmentId)
                .map(ConsignmentSummaryResponse::from)
                .orElseThrow(() -> new ConsignmentNotFoundException(consignmentId));
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping
    public ResponseEntity<ConsignmentSummaryResponse> createConsignment(@Valid @RequestBody ConsignmentCreateRequest request) {
        return ResponseEntity.ok(creationService.create(request));
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @DeleteMapping("/{consignmentId}")
    public ResponseEntity<Void> deleteConsignment(@PathVariable UUID consignmentId) {
        creationService.delete(consignmentId);
        return ResponseEntity.noContent().build();
    }
}
