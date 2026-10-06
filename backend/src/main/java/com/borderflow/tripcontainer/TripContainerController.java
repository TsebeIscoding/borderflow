package com.borderflow.tripcontainer;

import com.borderflow.common.ContainerNotFoundException;
import com.borderflow.common.TripNotFoundException;
import com.borderflow.container.ContainerMaster;
import com.borderflow.container.ContainerMasterRepository;
import com.borderflow.container.ContainerState;
import com.borderflow.container.ContainerStateRepository;
import com.borderflow.container.ContainerSummaryResponse;
import com.borderflow.trip.TripMaster;
import com.borderflow.trip.TripMasterRepository;
import com.borderflow.trip.TripState;
import com.borderflow.trip.TripStateRepository;
import com.borderflow.trip.TripSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * The Trip-Container association the design calls for. Deliberately a
 * separate controller/package rather than folded into TripController
 * or ContainerController, since it genuinely operates on both entities
 * at once rather than belonging to either one.
 */
@RestController
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class TripContainerController {

    private final TripContainerService tripContainerService;
    private final TripMasterRepository tripMasterRepository;
    private final TripStateRepository tripStateRepository;
    private final ContainerMasterRepository containerMasterRepository;
    private final ContainerStateRepository containerStateRepository;

    public TripContainerController(
            TripContainerService tripContainerService,
            TripMasterRepository tripMasterRepository,
            TripStateRepository tripStateRepository,
            ContainerMasterRepository containerMasterRepository,
            ContainerStateRepository containerStateRepository
    ) {
        this.tripContainerService = tripContainerService;
        this.tripMasterRepository = tripMasterRepository;
        this.tripStateRepository = tripStateRepository;
        this.containerMasterRepository = containerMasterRepository;
        this.containerStateRepository = containerStateRepository;
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping("/api/trips/{tripId}/containers/{containerId}")
    public ResponseEntity<Void> link(@PathVariable UUID tripId, @PathVariable UUID containerId) {
        tripContainerService.link(tripId, containerId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @DeleteMapping("/api/trips/{tripId}/containers/{containerId}")
    public ResponseEntity<Void> unlink(@PathVariable UUID tripId, @PathVariable UUID containerId) {
        tripContainerService.unlink(tripId, containerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/trips/{tripId}/containers")
    public List<ContainerSummaryResponse> containersOnTrip(@PathVariable UUID tripId) {
        return tripContainerService.containersOnTrip(tripId).stream()
                .map(link -> {
                    ContainerMaster master = containerMasterRepository.findById(link.getContainerId())
                            .orElseThrow(() -> new ContainerNotFoundException(link.getContainerId()));
                    ContainerState state = containerStateRepository.findById(link.getContainerId())
                            .orElseThrow(() -> new ContainerNotFoundException(link.getContainerId()));
                    return ContainerSummaryResponse.from(master, state);
                })
                .toList();
    }

    @GetMapping("/api/containers/{containerId}/trips")
    public List<TripSummaryResponse> tripsForContainer(@PathVariable UUID containerId) {
        return tripContainerService.tripsForContainer(containerId).stream()
                .map(link -> {
                    TripMaster master = tripMasterRepository.findById(link.getTripId())
                            .orElseThrow(() -> new TripNotFoundException(link.getTripId()));
                    TripState state = tripStateRepository.findById(link.getTripId())
                            .orElseThrow(() -> new TripNotFoundException(link.getTripId()));
                    return TripSummaryResponse.from(master, state);
                })
                .toList();
    }
}
