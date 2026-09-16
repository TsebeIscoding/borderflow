package com.borderflow.client;

import java.util.UUID;

/** Read-only. Never includes contact info -- see ClientCore's class javadoc for why. */
public record ClientSummaryResponse(
        UUID clientId,
        String name
) {
    static ClientSummaryResponse from(ClientCore client) {
        return new ClientSummaryResponse(client.getClientId(), client.getName());
    }
}
