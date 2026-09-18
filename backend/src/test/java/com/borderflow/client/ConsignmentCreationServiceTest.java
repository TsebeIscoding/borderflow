package com.borderflow.client;

import com.borderflow.common.ClientNotFoundException;
import com.borderflow.common.OriginSiteOnlyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsignmentCreationServiceTest {

    @Mock
    private ConsignmentRepository consignmentRepository;
    @Mock
    private ClientCoreRepository clientCoreRepository;

    @Test
    void createSucceedsAtDepotWhenClientExists() {
        ConsignmentCreationService service = new ConsignmentCreationService(consignmentRepository, clientCoreRepository, "depot");
        UUID clientId = UUID.randomUUID();
        when(clientCoreRepository.existsById(clientId)).thenReturn(true);

        ConsignmentSummaryResponse response = service.create(new ConsignmentCreateRequest(clientId, "Test consignment"));

        assertThat(response.clientId()).isEqualTo(clientId);
        assertThat(response.description()).isEqualTo("Test consignment");
        verify(consignmentRepository).save(ArgumentMatchers.any());
    }

    @Test
    void createFailsCleanlyWhenReferencedClientDoesNotExist() {
        ConsignmentCreationService service = new ConsignmentCreationService(consignmentRepository, clientCoreRepository, "depot");
        UUID clientId = UUID.randomUUID();
        when(clientCoreRepository.existsById(clientId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new ConsignmentCreateRequest(clientId, "Orphan consignment")))
                .isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    void createIsRejectedAtNonOriginSites() {
        ConsignmentCreationService service = new ConsignmentCreationService(consignmentRepository, clientCoreRepository, "border");

        assertThatThrownBy(() -> service.create(new ConsignmentCreateRequest(UUID.randomUUID(), "desc")))
                .isInstanceOf(OriginSiteOnlyException.class);
    }

    @Test
    void deleteIsRejectedAtNonOriginSites() {
        ConsignmentCreationService service = new ConsignmentCreationService(consignmentRepository, clientCoreRepository, "port");

        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(OriginSiteOnlyException.class);
    }
}
