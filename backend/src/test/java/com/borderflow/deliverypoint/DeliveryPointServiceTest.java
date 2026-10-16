package com.borderflow.deliverypoint;

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

@ExtendWith(MockitoExtension.class)
class DeliveryPointServiceTest {

    @Mock
    private DeliveryPointRepository deliveryPointRepository;

    @Test
    void createSucceedsAtDepotAndTrimsTheName() {
        DeliveryPointService service = new DeliveryPointService(deliveryPointRepository, "depot");

        DeliveryPointSummaryResponse response = service.create(new DeliveryPointCreateRequest("  Maseru  "));

        assertThat(response.name()).isEqualTo("Maseru");
        verify(deliveryPointRepository).save(ArgumentMatchers.any());
    }

    @Test
    void createIsRejectedAtNonOriginSites() {
        DeliveryPointService service = new DeliveryPointService(deliveryPointRepository, "border");

        assertThatThrownBy(() -> service.create(new DeliveryPointCreateRequest("Maseru")))
                .isInstanceOf(OriginSiteOnlyException.class);
    }

    @Test
    void deleteIsRejectedAtNonOriginSites() {
        DeliveryPointService service = new DeliveryPointService(deliveryPointRepository, "port");

        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(OriginSiteOnlyException.class);
    }
}
