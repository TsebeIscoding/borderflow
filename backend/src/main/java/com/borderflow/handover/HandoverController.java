package com.borderflow.handover;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
public class HandoverController {

    private final HandoverService handoverService;

    public HandoverController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    /**
     * Hands off custody of a trip from THIS site to another. Which site
     * "this" is comes from the running service instance's own
     * configuration (site.id), never from the request -- see
     * HandoverRequest's javadoc.
     *
     * OPERATOR only -- an AUDITOR's cross-site token authenticates them
     * everywhere for reading, but they never get write access anywhere,
     * by design (see docs/design/vertical-fragmentation-design.md).
     */
    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping("/{tripId}/handover")
    public ResponseEntity<HandoverResponse> handOff(
            @PathVariable UUID tripId,
            @Valid @RequestBody HandoverRequest request
    ) {
        HandoverResponse response = handoverService.handOff(tripId, request);
        return ResponseEntity.ok(response);
    }
}
