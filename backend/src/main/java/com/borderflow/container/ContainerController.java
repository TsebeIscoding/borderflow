package com.borderflow.container;

import com.borderflow.common.ContainerNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * Container CRUD. Read + relocate endpoints mirror TripController and
 * HandoverController's auth shape exactly. Create/delete are
 * origin-only -- see ContainerCreationService and
 * OriginSiteOnlyException's javadoc.
 */
@RestController
@RequestMapping("/api/containers")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class ContainerController {

    private final ContainerMasterRepository containerMasterRepository;
    private final ContainerStateRepository containerStateRepository;
    private final ContainerRelocationService relocationService;
    private final ContainerCreationService creationService;

    public ContainerController(
            ContainerMasterRepository containerMasterRepository,
            ContainerStateRepository containerStateRepository,
            ContainerRelocationService relocationService,
            ContainerCreationService creationService
    ) {
        this.containerMasterRepository = containerMasterRepository;
        this.containerStateRepository = containerStateRepository;
        this.relocationService = relocationService;
        this.creationService = creationService;
    }

    @GetMapping
    public List<ContainerSummaryResponse> listContainers() {
        return containerMasterRepository.findAll().stream()
                .map(master -> containerStateRepository.findById(master.getContainerId())
                        .map(state -> ContainerSummaryResponse.from(master, state))
                        .orElse(null))
                .filter(c -> c != null)
                .toList();
    }

    @GetMapping("/{containerId}")
    public ContainerSummaryResponse getContainer(@PathVariable UUID containerId) {
        ContainerMaster master = containerMasterRepository.findById(containerId)
                .orElseThrow(() -> new ContainerNotFoundException(containerId));
        ContainerState state = containerStateRepository.findById(containerId)
                .orElseThrow(() -> new ContainerNotFoundException(containerId));
        return ContainerSummaryResponse.from(master, state);
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping("/{containerId}/relocate")
    public ResponseEntity<ContainerRelocationResponse> relocate(
            @PathVariable UUID containerId,
            @Valid @RequestBody ContainerRelocationRequest request
    ) {
        return ResponseEntity.ok(relocationService.relocate(containerId, request));
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping
    public ResponseEntity<ContainerSummaryResponse> createContainer(@Valid @RequestBody ContainerCreateRequest request) {
        return ResponseEntity.ok(creationService.create(request));
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @DeleteMapping("/{containerId}")
    public ResponseEntity<Void> deleteContainer(@PathVariable UUID containerId) {
        creationService.delete(containerId);
        return ResponseEntity.noContent().build();
    }
}
