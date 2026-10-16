package com.borderflow.trip;

import com.borderflow.common.DeliveryPointNotFoundException;
import com.borderflow.common.OriginSiteOnlyException;
import com.borderflow.deliverypoint.DeliveryPointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the origin-site restriction and the delivery-point existence check.
 * The actual INSERT is exercised against the real database in the manual
 * verification pass documented in docs/testing/test-results.md.
 */
@ExtendWith(MockitoExtension.class)
class TripCreationServiceTest {

    @Mock
    private TripMasterRepository tripMasterRepository;
    @Mock
    private TripStateRepository tripStateRepository;
    @Mock
    private DeliveryPointRepository deliveryPointRepository;

    @Test
    void createSucceedsAtDepot() {
        TripCreationService service = new TripCreationService(tripMasterRepository, tripStateRepository, deliveryPointRepository, "depot");
        UUID deliveryPointId = UUID.randomUUID();
        when(deliveryPointRepository.existsById(deliveryPointId)).thenReturn(true);

        TripSummaryResponse response = service.create(new TripCreateRequest("border", deliveryPointId));

        assertThat(response.originSiteId()).isEqualTo("depot");
        assertThat(response.destinationSiteId()).isEqualTo("border");
        assertThat(response.deliveryPointId()).isEqualTo(deliveryPointId);
        assertThat(response.status()).isEqualTo("AtOrigin");
        assertThat(response.lamportTs()).isEqualTo(1);
        verify(tripMasterRepository).save(org.mockito.ArgumentMatchers.any());
        verify(tripStateRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createFailsCleanlyWhenDeliveryPointDoesNotExist() {
        TripCreationService service = new TripCreationService(tripMasterRepository, tripStateRepository, deliveryPointRepository, "depot");
        UUID deliveryPointId = UUID.randomUUID();
        when(deliveryPointRepository.existsById(deliveryPointId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new TripCreateRequest("destination", deliveryPointId)))
                .isInstanceOf(DeliveryPointNotFoundException.class);
    }

    @Test
    void createIsRejectedAtNonOriginSites() {
        TripCreationService service = new TripCreationService(tripMasterRepository, tripStateRepository, deliveryPointRepository, "border");

        assertThatThrownBy(() -> service.create(new TripCreateRequest("port", UUID.randomUUID())))
                .isInstanceOf(OriginSiteOnlyException.class)
                .hasMessageContaining("depot")
                .hasMessageContaining("border");
    }

    @Test
    void deleteIsRejectedAtNonOriginSites() {
        TripCreationService service = new TripCreationService(tripMasterRepository, tripStateRepository, deliveryPointRepository, "port");

        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(OriginSiteOnlyException.class);
    }
}
