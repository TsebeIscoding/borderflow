package com.borderflow.client;

import com.borderflow.common.ClientNotFoundException;
import com.borderflow.common.DeliveryPointNotFoundException;
import com.borderflow.common.OriginSiteOnlyException;
import com.borderflow.deliverypoint.DeliveryPointRepository;
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
    @Mock
    private DeliveryPointRepository deliveryPointRepository;

    @Test
    void createSucceedsAtDepotWhenClientExists() {
        ConsignmentCreationService service = new ConsignmentCreationService(consignmentRepository, clientCoreRepository, deliveryPointRepository, "depot");
        UUID clientId = UUID.randomUUID();
        UUID deliveryPointId = UUID.randomUUID();
        when(clientCoreRepository.existsById(clientId)).thenReturn(true);
        when(deliveryPointRepository.existsById(deliveryPointId)).thenReturn(true);

        ConsignmentSummaryResponse response = service.create(new ConsignmentCreateRequest(clientId, "Test consignment", deliveryPointId));

        assertThat(response.clientId()).isEqualTo(clientId);
        assertThat(response.deliveryPointId()).isEqualTo(deliveryPointId);
        assertThat(response.description()).isEqualTo("Test consignment");
        verify(consignmentRepository).save(ArgumentMatchers.any());
    }

    @Test
    void createFailsCleanlyWhenReferencedClientDoesNotExist() {
        ConsignmentCreationService service = new ConsignmentCreationService(consignmentRepository, clientCoreRepository, deliveryPointRepository, "depot");
        UUID clientId = UUID.randomUUID();
        when(clientCoreRepository.existsById(clientId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new ConsignmentCreateRequest(clientId, "Orphan consignment", UUID.randomUUID())))
                .isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    void createFailsCleanlyWhenDeliveryPointDoesNotExist() {
        ConsignmentCreationService service = new ConsignmentCreationService(consignmentRepository, clientCoreRepository, deliveryPointRepository, "depot");
        UUID clientId = UUID.randomUUID();
        UUID deliveryPointId = UUID.randomUUID();
        when(clientCoreRepository.existsById(clientId)).thenReturn(true);
        when(deliveryPointRepository.existsById(deliveryPointId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new ConsignmentCreateRequest(clientId, "No such town", deliveryPointId)))
                .isInstanceOf(DeliveryPointNotFoundException.class);
    }

    @Test
    void createIsRejectedAtNonOriginSites() {
        ConsignmentCreationService service = new ConsignmentCreationService(consignmentRepository, clientCoreRepository, deliveryPointRepository, "border");

        assertThatThrownBy(() -> service.create(new ConsignmentCreateRequest(UUID.randomUUID(), "desc", UUID.randomUUID())))
                .isInstanceOf(OriginSiteOnlyException.class);
    }

    @Test
    void deleteIsRejectedAtNonOriginSites() {
        ConsignmentCreationService service = new ConsignmentCreationService(consignmentRepository, clientCoreRepository, deliveryPointRepository, "port");

        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(OriginSiteOnlyException.class);
    }
}
