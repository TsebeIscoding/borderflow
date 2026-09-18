package com.borderflow.driver;

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

/** Same shape as VehicleCreationServiceTest. */
@ExtendWith(MockitoExtension.class)
class DriverCreationServiceTest {

    @Mock
    private DriverProfileRepository driverProfileRepository;
    @Mock
    private DriverAvailabilityRepository driverAvailabilityRepository;

    @Test
    void createSucceedsAtDepot() {
        DriverCreationService service = new DriverCreationService(driverProfileRepository, driverAvailabilityRepository, "depot");

        DriverSummaryResponse response = service.create(new DriverCreateRequest("Test Driver", "LIC-0001", "0110000000"));

        assertThat(response.name()).isEqualTo("Test Driver");
        assertThat(response.currentSiteId()).isEqualTo("depot");
        assertThat(response.status()).isEqualTo("Available");
        assertThat(response.lamportTs()).isEqualTo(1);
        verify(driverProfileRepository).save(ArgumentMatchers.any());
        verify(driverAvailabilityRepository).save(ArgumentMatchers.any());
    }

    @Test
    void createIsRejectedAtNonOriginSites() {
        DriverCreationService service = new DriverCreationService(driverProfileRepository, driverAvailabilityRepository, "border");

        assertThatThrownBy(() -> service.create(new DriverCreateRequest("Another Driver", "LIC-0002", null)))
                .isInstanceOf(OriginSiteOnlyException.class);
    }

    @Test
    void deleteIsRejectedAtNonOriginSites() {
        DriverCreationService service = new DriverCreationService(driverProfileRepository, driverAvailabilityRepository, "port");

        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(OriginSiteOnlyException.class);
    }
}
