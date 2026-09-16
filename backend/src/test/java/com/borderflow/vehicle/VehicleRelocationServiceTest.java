package com.borderflow.vehicle;

import com.borderflow.common.InvalidVehicleMoveException;
import com.borderflow.common.VehicleNotFoundException;
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

/** Same shape as ContainerRelocationServiceTest. */
@ExtendWith(MockitoExtension.class)
class VehicleRelocationServiceTest {

    @Mock
    private VehicleProfileRepository vehicleProfileRepository;
    @Mock
    private VehicleAvailabilityRepository vehicleAvailabilityRepository;

    private VehicleRelocationService service;

    private static final UUID VEHICLE_ID = UUID.randomUUID();
    private static final String THIS_SITE = "border";

    @BeforeEach
    void setUp() {
        service = new VehicleRelocationService(vehicleProfileRepository, vehicleAvailabilityRepository, THIS_SITE);
    }

    @Test
    void rejectsRelocationWhenThisSiteDoesNotCurrentlyHoldTheVehicle() {
        VehicleAvailability availability = new VehicleAvailability(VEHICLE_ID, "Available", "port", 3);
        when(vehicleProfileRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(new VehicleProfile()));
        when(vehicleAvailabilityRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(availability));

        assertThatThrownBy(() -> service.relocate(VEHICLE_ID, new VehicleRelocationRequest("destination", "liaison-01")))
                .isInstanceOf(InvalidVehicleMoveException.class)
                .hasMessageContaining("port")
                .hasMessageContaining("border");
    }

    @Test
    void throwsVehicleNotFoundWhenProfileIsMissing() {
        when(vehicleProfileRepository.findById(VEHICLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.relocate(VEHICLE_ID, new VehicleRelocationRequest("port", "liaison-01")))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void successfulRelocationAdvancesLamportTimestamp() {
        VehicleAvailability availability = new VehicleAvailability(VEHICLE_ID, "Available", THIS_SITE, 1);
        when(vehicleProfileRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(new VehicleProfile()));
        when(vehicleAvailabilityRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(availability));

        VehicleRelocationResponse response = service.relocate(VEHICLE_ID, new VehicleRelocationRequest("port", "liaison-01"));

        assertThat(response.lamportTs()).isEqualTo(2);
        assertThat(response.fromSiteId()).isEqualTo(THIS_SITE);
        assertThat(response.toSiteId()).isEqualTo("port");
        verify(vehicleAvailabilityRepository).save(availability);
    }
}
