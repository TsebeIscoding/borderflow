package com.borderflow.vehicle;

import com.borderflow.common.OriginSiteOnlyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/** Same shape as ContainerCreationServiceTest. */
@ExtendWith(MockitoExtension.class)
class VehicleCreationServiceTest {

    @Mock
    private VehicleProfileRepository vehicleProfileRepository;
    @Mock
    private VehicleAvailabilityRepository vehicleAvailabilityRepository;

    @Test
    void createSucceedsAtDepot() {
        VehicleCreationService service = new VehicleCreationService(vehicleProfileRepository, vehicleAvailabilityRepository, "depot");

        VehicleSummaryResponse response = service.create(new VehicleCreateRequest("VEH-0001", new BigDecimal("15.0")));

        assertThat(response.registrationNumber()).isEqualTo("VEH-0001");
        assertThat(response.currentSiteId()).isEqualTo("depot");
        assertThat(response.status()).isEqualTo("Available");
        assertThat(response.lamportTs()).isEqualTo(1);
        verify(vehicleProfileRepository).save(ArgumentMatchers.any());
        verify(vehicleAvailabilityRepository).save(ArgumentMatchers.any());
    }

    @Test
    void createIsRejectedAtNonOriginSites() {
        VehicleCreationService service = new VehicleCreationService(vehicleProfileRepository, vehicleAvailabilityRepository, "border");

        assertThatThrownBy(() -> service.create(new VehicleCreateRequest("VEH-0002", new BigDecimal("10.0"))))
                .isInstanceOf(OriginSiteOnlyException.class);
    }

    @Test
    void deleteIsRejectedAtNonOriginSites() {
        VehicleCreationService service = new VehicleCreationService(vehicleProfileRepository, vehicleAvailabilityRepository, "port");

        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(OriginSiteOnlyException.class);
    }
}
