package com.borderflow.trip;

import com.borderflow.common.TripNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * Trip CRUD. Read endpoints (list, get) are the same as always -- every
 * field comes from THIS site's own local Postgres, no cross-site call
 * involved, because replication has already brought every other
 * site's writes in. Create and delete are origin-only (see
 * TripCreationService and OriginSiteOnlyException's javadoc) -- there
 * is no update-the-Master-fields endpoint, since those fields are
 * immutable by design once a trip exists; the State fragment's own
 * "update" is HandoverController's handOff, a separate, already
 * -existing endpoint.
 *
 * Both OPERATOR and AUDITOR can read -- reading the manifest is exactly
 * what a cross-site auditor's token exists for. Create/delete/handover
 * are all OPERATOR-only.
 */
@RestController
@RequestMapping("/api/trips")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class TripController {

    private final TripMasterRepository tripMasterRepository;
    private final TripStateRepository tripStateRepository;
    private final TripCreationService creationService;

    public TripController(
            TripMasterRepository tripMasterRepository,
            TripStateRepository tripStateRepository,
            TripCreationService creationService
    ) {
        this.tripMasterRepository = tripMasterRepository;
        this.tripStateRepository = tripStateRepository;
        this.creationService = creationService;
    }

    @GetMapping
    public List<TripSummaryResponse> listTrips() {
        return tripMasterRepository.findAll().stream()
                .map(master -> tripStateRepository.findById(master.getTripId())
                        .map(state -> TripSummaryResponse.from(master, state))
                        .orElse(null))
                .filter(t -> t != null)
                .toList();
    }

    @GetMapping("/{tripId}")
    public TripSummaryResponse getTrip(@PathVariable UUID tripId) {
        TripMaster master = tripMasterRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
        TripState state = tripStateRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
        return TripSummaryResponse.from(master, state);
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping
    public ResponseEntity<TripSummaryResponse> createTrip(@Valid @RequestBody TripCreateRequest request) {
        return ResponseEntity.ok(creationService.create(request));
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> deleteTrip(@PathVariable UUID tripId) {
        creationService.delete(tripId);
        return ResponseEntity.noContent().build();
    }
}
