package com.borderflow.client;

import com.borderflow.common.ClientNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * Read-only, both roles. No write endpoint exists here at all --
 * client_core is only ever written at Depot via the migration seed
 * data in this codebase; nothing here ever creates or edits a client.
 * Adding one would need PII-handling considerations this project
 * hasn't taken on.
 */
@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class ClientController {

    private final ClientCoreRepository clientCoreRepository;

    public ClientController(ClientCoreRepository clientCoreRepository) {
        this.clientCoreRepository = clientCoreRepository;
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
}
