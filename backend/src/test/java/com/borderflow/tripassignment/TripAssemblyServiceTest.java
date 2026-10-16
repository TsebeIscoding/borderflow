package com.borderflow.tripassignment;

import com.borderflow.common.InvalidTripAssignmentException;
import com.borderflow.driver.DriverAvailability;
import com.borderflow.driver.DriverAvailabilityRepository;
import com.borderflow.trip.TripMasterRepository;
import com.borderflow.trip.TripState;
import com.borderflow.trip.TripStateRepository;
import com.borderflow.vehicle.VehicleAvailability;
import com.borderflow.vehicle.VehicleAvailabilityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripAssemblyServiceTest {

    @Mock
    private TripMasterRepository tripMasterRepository;
    @Mock
    private TripStateRepository tripStateRepository;
    @Mock
    private VehicleAvailabilityRepository vehicleAvailabilityRepository;
    @Mock
    private DriverAvailabilityRepository driverAvailabilityRepository;
    @Mock
    private TripAssignmentRepository tripAssignmentRepository;

    private static final UUID TRIP_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();
    private static final UUID OTHER_VEHICLE_ID = UUID.randomUUID();
    private static final UUID DRIVER_ID = UUID.randomUUID();

    private TripAssemblyService serviceAt(String siteId) {
        return new TripAssemblyService(tripMasterRepository, tripStateRepository, vehicleAvailabilityRepository,
                driverAvailabilityRepository, tripAssignmentRepository, siteId);
    }

    private TripState tripAt(String site, String status) {
        TripState state = new TripState(TRIP_ID, status, site, 5);
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(state));
        return state;
    }

    private void vehicleAt(UUID id, String site) {
        when(vehicleAvailabilityRepository.findById(id)).thenReturn(Optional.of(new VehicleAvailability(id, "Available", site, 1)));
    }

    @Test
    void assignVehicleSetsItOnTheTripAndRecordsAHistoryRow() {
        TripState state = tripAt("depot", "AtOrigin");
        vehicleAt(VEHICLE_ID, "depot");

        serviceAt("depot").assignVehicle(TRIP_ID, VEHICLE_ID);

        assertThat(state.getVehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(state.getLamportTs()).isEqualTo(6);
        ArgumentCaptor<TripAssignment> saved = ArgumentCaptor.forClass(TripAssignment.class);
        verify(tripAssignmentRepository).save(saved.capture());
        assertThat(saved.getValue().getAction()).isEqualTo("ASSIGNED");
        assertThat(saved.getValue().getResourceType()).isEqualTo("VEHICLE");
        assertThat(saved.getValue().getResourceId()).isEqualTo(VEHICLE_ID);
        verify(tripStateRepository).save(state);
    }

    @Test
    void assignVehicleIsRejectedWhenTheTripIsHeldAtAnotherSite() {
        tripAt("border", "Arrived");

        assertThatThrownBy(() -> serviceAt("depot").assignVehicle(TRIP_ID, VEHICLE_ID))
                .isInstanceOf(InvalidTripAssignmentException.class)
                .hasMessageContaining("border");
    }

    @Test
    void assignVehicleIsRejectedWhenTheVehicleIsHeldAtAnotherSite() {
        tripAt("depot", "AtOrigin");
        vehicleAt(VEHICLE_ID, "port");

        assertThatThrownBy(() -> serviceAt("depot").assignVehicle(TRIP_ID, VEHICLE_ID))
                .isInstanceOf(InvalidTripAssignmentException.class)
                .hasMessageContaining("port");
    }

    @Test
    void assignVehicleIsRejectedWhenItIsOnAnotherActiveTrip() {
        tripAt("depot", "AtOrigin");
        vehicleAt(VEHICLE_ID, "depot");
        when(tripStateRepository.existsByVehicleIdAndTripIdNotAndStatusNot(VEHICLE_ID, TRIP_ID, "Delivered")).thenReturn(true);

        assertThatThrownBy(() -> serviceAt("depot").assignVehicle(TRIP_ID, VEHICLE_ID))
                .isInstanceOf(InvalidTripAssignmentException.class)
                .hasMessageContaining("another active trip");
        verify(tripAssignmentRepository, never()).save(any());
    }

    @Test
    void assignVehicleIsRejectedForADeliveredTrip() {
        tripAt("destination", "Delivered");

        assertThatThrownBy(() -> serviceAt("destination").assignVehicle(TRIP_ID, VEHICLE_ID))
                .isInstanceOf(InvalidTripAssignmentException.class)
                .hasMessageContaining("Delivered");
    }

    @Test
    void assigningADifferentVehicleReleasesTheOldOneFirst() {
        TripState state = tripAt("depot", "AtOrigin");
        state.assignVehicle(VEHICLE_ID, 5);
        vehicleAt(OTHER_VEHICLE_ID, "depot");

        serviceAt("depot").assignVehicle(TRIP_ID, OTHER_VEHICLE_ID);

        ArgumentCaptor<TripAssignment> saved = ArgumentCaptor.forClass(TripAssignment.class);
        verify(tripAssignmentRepository, times(2)).save(saved.capture());
        List<TripAssignment> rows = saved.getAllValues();
        assertThat(rows.get(0).getAction()).isEqualTo("RELEASED");
        assertThat(rows.get(0).getResourceId()).isEqualTo(VEHICLE_ID);
        assertThat(rows.get(1).getAction()).isEqualTo("ASSIGNED");
        assertThat(rows.get(1).getResourceId()).isEqualTo(OTHER_VEHICLE_ID);
        assertThat(state.getVehicleId()).isEqualTo(OTHER_VEHICLE_ID);
    }

    @Test
    void assigningTheVehicleAlreadyOnTheTripChangesNothing() {
        TripState state = tripAt("depot", "AtOrigin");
        state.assignVehicle(VEHICLE_ID, 5);
        vehicleAt(VEHICLE_ID, "depot");

        serviceAt("depot").assignVehicle(TRIP_ID, VEHICLE_ID);

        verify(tripAssignmentRepository, never()).save(any());
        verify(tripStateRepository, never()).save(any());
    }

    @Test
    void releaseVehicleClearsItAndRecordsAHistoryRow() {
        TripState state = tripAt("depot", "AtOrigin");
        state.assignVehicle(VEHICLE_ID, 5);

        serviceAt("depot").releaseVehicle(TRIP_ID);

        assertThat(state.getVehicleId()).isNull();
        assertThat(state.getLamportTs()).isEqualTo(6);
        ArgumentCaptor<TripAssignment> saved = ArgumentCaptor.forClass(TripAssignment.class);
        verify(tripAssignmentRepository).save(saved.capture());
        assertThat(saved.getValue().getAction()).isEqualTo("RELEASED");
    }

    @Test
    void releaseVehicleWhenNoneIsAssignedChangesNothing() {
        tripAt("depot", "AtOrigin");

        serviceAt("depot").releaseVehicle(TRIP_ID);

        verify(tripAssignmentRepository, never()).save(any());
        verify(tripStateRepository, never()).save(any());
    }

    @Test
    void assignDriverSetsItOnTheTripAndRecordsAHistoryRow() {
        TripState state = tripAt("depot", "AtOrigin");
        when(driverAvailabilityRepository.findById(DRIVER_ID))
                .thenReturn(Optional.of(new DriverAvailability(DRIVER_ID, "Available", "depot", 1)));

        serviceAt("depot").assignDriver(TRIP_ID, DRIVER_ID);

        assertThat(state.getDriverId()).isEqualTo(DRIVER_ID);
        ArgumentCaptor<TripAssignment> saved = ArgumentCaptor.forClass(TripAssignment.class);
        verify(tripAssignmentRepository).save(saved.capture());
        assertThat(saved.getValue().getResourceType()).isEqualTo("DRIVER");
    }

    @Test
    void assignDriverIsRejectedWhenTheyAreOnAnotherActiveTrip() {
        tripAt("depot", "AtOrigin");
        when(driverAvailabilityRepository.findById(DRIVER_ID))
                .thenReturn(Optional.of(new DriverAvailability(DRIVER_ID, "Available", "depot", 1)));
        when(tripStateRepository.existsByDriverIdAndTripIdNotAndStatusNot(DRIVER_ID, TRIP_ID, "Delivered")).thenReturn(true);

        assertThatThrownBy(() -> serviceAt("depot").assignDriver(TRIP_ID, DRIVER_ID))
                .isInstanceOf(InvalidTripAssignmentException.class);
    }
}
