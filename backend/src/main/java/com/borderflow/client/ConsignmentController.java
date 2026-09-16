package com.borderflow.client;

import com.borderflow.common.ConsignmentNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Read-only, both roles -- same reasoning as ClientController. */
@RestController
@RequestMapping("/api/consignments")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class ConsignmentController {

    private final ConsignmentRepository consignmentRepository;

    public ConsignmentController(ConsignmentRepository consignmentRepository) {
        this.consignmentRepository = consignmentRepository;
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
}
