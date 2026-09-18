package com.borderflow.client;

import com.borderflow.common.EntityInUseException;
import com.borderflow.common.OriginSiteOnlyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Client has no State fragment and no relocation concept -- it's
 * static Master data. Create/delete are still origin-only, same as
 * every other Master fragment (see OriginSiteOnlyException's javadoc).
 * Deleting a client that still has Consignments referencing it via
 * the `consignment.client_id` foreign key correctly fails with
 * EntityInUseException rather than a raw SQL error.
 */
@Service
public class ClientCreationService {

    private final ClientCoreRepository clientCoreRepository;
    private final String thisSiteId;

    public ClientCreationService(ClientCoreRepository clientCoreRepository, @Value("${site.id}") String thisSiteId) {
        this.clientCoreRepository = clientCoreRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public ClientSummaryResponse create(ClientCreateRequest request) {
        requireOriginSite();
        ClientCore client = new ClientCore(UUID.randomUUID(), request.name());
        clientCoreRepository.save(client);
        return ClientSummaryResponse.from(client);
    }

    @Transactional
    public void delete(UUID clientId) {
        requireOriginSite();
        try {
            // deleteById() alone does not execute the DELETE immediately --
            // Hibernate normally defers flushing until the transaction
            // commits, which happens AFTER this method returns, outside
            // this try/catch. Found via testing: without the explicit
            // flush() here, a real foreign-key violation (a Consignment
            // still referencing this Client) propagated as an unhandled
            // exception from the servlet layer instead of being caught
            // below, producing a confusing generic error instead of a
            // clean 409. flush() forces the DELETE (and its constraint
            // check) to happen right here, where it can actually be caught.
            clientCoreRepository.deleteById(clientId);
            clientCoreRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EntityInUseException("Client " + clientId + " cannot be deleted -- it still has consignments referencing it");
        }
    }

    private void requireOriginSite() {
        if (!"depot".equals(thisSiteId)) {
            throw new OriginSiteOnlyException("Client", thisSiteId);
        }
    }
}
