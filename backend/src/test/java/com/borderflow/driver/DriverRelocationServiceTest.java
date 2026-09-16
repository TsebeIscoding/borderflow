package com.borderflow.driver;

import com.borderflow.common.DriverNotFoundException;
import com.borderflow.common.InvalidDriverMoveException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Same shape as VehicleRelocationServiceTest. */
@ExtendWith(MockitoExtension.class)
class DriverRelocationServiceTest {

    @Mock
    private DriverProfileRepository driverProfileRepository;
    @Mock
    private DriverAvailabilityRepository driverAvailabilityRepository;

    private DriverRelocationService service;

    private static final UUID DRIVER_ID = UUID.randomUUID();
    private static final String THIS_SITE = "border";

    @BeforeEach
    void setUp() {
        service = new DriverRelocationService(driverProfileRepository, driverAvailabilityRepository, THIS_SITE);
    }

    @Test
    void rejectsRelocationWhenThisSiteDoesNotCurrentlyHoldTheDriver() {
        DriverAvailability availability = new DriverAvailability(DRIVER_ID, "Available", "port", 3);
        when(driverProfileRepository.findById(DRIVER_ID)).thenReturn(Optional.of(new DriverProfile()));
        when(driverAvailabilityRepository.findById(DRIVER_ID)).thenReturn(Optional.of(availability));

        assertThatThrownBy(() -> service.relocate(DRIVER_ID, new DriverRelocationRequest("destination", "liaison-01")))
                .isInstanceOf(InvalidDriverMoveException.class)
                .hasMessageContaining("port")
                .hasMessageContaining("border");
    }

    @Test
    void throwsDriverNotFoundWhenProfileIsMissing() {
        when(driverProfileRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.relocate(DRIVER_ID, new DriverRelocationRequest("port", "liaison-01")))
                .isInstanceOf(DriverNotFoundException.class);
    }

    @Test
    void successfulRelocationAdvancesLamportTimestamp() {
        DriverAvailability availability = new DriverAvailability(DRIVER_ID, "Available", THIS_SITE, 1);
        when(driverProfileRepository.findById(DRIVER_ID)).thenReturn(Optional.of(new DriverProfile()));
        when(driverAvailabilityRepository.findById(DRIVER_ID)).thenReturn(Optional.of(availability));

        DriverRelocationResponse response = service.relocate(DRIVER_ID, new DriverRelocationRequest("port", "liaison-01"));

        assertThat(response.lamportTs()).isEqualTo(2);
        assertThat(response.fromSiteId()).isEqualTo(THIS_SITE);
        assertThat(response.toSiteId()).isEqualTo("port");
        verify(driverAvailabilityRepository).save(availability);
    }
}
