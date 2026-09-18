package com.borderflow.client;

import com.borderflow.common.ClientNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * Client CRUD, both roles for read, OPERATOR + origin-only for
 * create/delete (see ClientCreationService). Note this only ever
 * touches `client_core` (name only) -- `client_contact` (the PII
 * table) has no entity, repository, endpoint, or any code path
 * anywhere in this codebase, regardless of role or site. That's not
 * a gap this controller has; it's a line this project deliberately
 * never crosses.
 */
@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class ClientController {

    private final ClientCoreRepository clientCoreRepository;
    private final ClientCreationService creationService;

    public ClientController(ClientCoreRepository clientCoreRepository, ClientCreationService creationService) {
        this.clientCoreRepository = clientCoreRepository;
        this.creationService = creationService;
    }

    @GetMapping
    public List<ClientSummaryResponse> listClients() {
        return clientCoreRepository.findAll().stream()
                .map(ClientSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{clientId}")
    public ClientSummaryResponse getClient(@PathVariable UUID clientId) {
        return clientCoreRepository.findById(clientId)
                .map(ClientSummaryResponse::from)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping
    public ResponseEntity<ClientSummaryResponse> createClient(@Valid @RequestBody ClientCreateRequest request) {
        return ResponseEntity.ok(creationService.create(request));
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @DeleteMapping("/{clientId}")
    public ResponseEntity<Void> deleteClient(@PathVariable UUID clientId) {
        creationService.delete(clientId);
        return ResponseEntity.noContent().build();
    }
}
