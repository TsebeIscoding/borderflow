package com.borderflow.handover;

import com.borderflow.common.InvalidHandoverException;
import com.borderflow.common.TripNotFoundException;
import com.borderflow.container.ContainerState;
import com.borderflow.container.ContainerStateRepository;
import com.borderflow.driver.DriverAvailability;
import com.borderflow.driver.DriverAvailabilityRepository;
import com.borderflow.trip.TripMaster;
import com.borderflow.trip.TripMasterRepository;
import com.borderflow.trip.TripState;
import com.borderflow.trip.TripStateRepository;
import com.borderflow.tripcontainer.TripContainer;
import com.borderflow.tripcontainer.TripContainerRepository;
import com.borderflow.vehicle.VehicleAvailability;
import com.borderflow.vehicle.VehicleAvailabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the business rules HandoverService is responsible for
 * -- the ones the database does NOT enforce (site-holds-trip, terminal
 * status). Conflict resolution and event idempotency are deliberately
 * NOT re-tested here; they're proven against the real database in
 * scripts/conflict-resolution-test.sh and covered in
 * docs/testing/test-results.md. Re-asserting them against mocks here
 * would just be testing that the mock does what we told it to.
 */
@ExtendWith(MockitoExtension.class)
class HandoverServiceTest {

    @Mock
    private TripMasterRepository tripMasterRepository;
    @Mock
    private TripStateRepository tripStateRepository;
    @Mock
    private HandoverRepository handoverRepository;
    @Mock
    private TripContainerRepository tripContainerRepository;
    @Mock
    private ContainerStateRepository containerStateRepository;
    @Mock
    private VehicleAvailabilityRepository vehicleAvailabilityRepository;
    @Mock
    private DriverAvailabilityRepository driverAvailabilityRepository;

    private HandoverService service;

    private static final UUID TRIP_ID = UUID.randomUUID();
    private static final String THIS_SITE = "border";

    @BeforeEach
    void setUp() {
        service = new HandoverService(tripMasterRepository, tripStateRepository, handoverRepository,
                tripContainerRepository, containerStateRepository, vehicleAvailabilityRepository, driverAvailabilityRepository, THIS_SITE);
    }

    @Test
    void rejectsHandoverWhenThisSiteDoesNotCurrentlyHoldTheTrip() {
        TripMaster master = new TripMaster(TRIP_ID, "depot", "destination", null);
        TripState state = new TripState(TRIP_ID, "Arrived", "port", 3); // held at "port", not "border"
        when(tripMasterRepository.findById(TRIP_ID)).thenReturn(Optional.of(master));
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service.handOff(TRIP_ID, new HandoverRequest("destination", "liaison-01")))
                .isInstanceOf(InvalidHandoverException.class)
                .hasMessageContaining("port")
                .hasMessageContaining("border");
    }

    @Test
    void rejectsHandoverOnAnAlreadyDeliveredTrip() {
        TripMaster master = new TripMaster(TRIP_ID, "depot", "destination", null);
        TripState state = new TripState(TRIP_ID, "Delivered", THIS_SITE, 4);
        when(tripMasterRepository.findById(TRIP_ID)).thenReturn(Optional.of(master));
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service.handOff(TRIP_ID, new HandoverRequest("destination", "liaison-01")))
                .isInstanceOf(InvalidHandoverException.class)
                .hasMessageContaining("already Delivered");
    }

    @Test
    void throwsTripNotFoundWhenTripMasterIsMissing() {
        when(tripMasterRepository.findById(TRIP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handOff(TRIP_ID, new HandoverRequest("border", "liaison-01")))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void handingOffToTheFinalDestinationMarksTripDelivered() {
        TripMaster master = new TripMaster(TRIP_ID, "depot", "destination", null);
        TripState state = new TripState(TRIP_ID, "Arrived", THIS_SITE, 3);
        when(tripMasterRepository.findById(TRIP_ID)).thenReturn(Optional.of(master));
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(state));

        HandoverResponse response = service.handOff(TRIP_ID, new HandoverRequest("destination", "liaison-01"));

        assertThat(response.newStatus()).isEqualTo("Delivered");
        assertThat(response.lamportTs()).isEqualTo(4); // advanced by exactly 1
        assertThat(response.fromSiteId()).isEqualTo(THIS_SITE);
        verify(handoverRepository).save(any(Handover.class));
        verify(tripStateRepository).save(state);
    }

    @Test
    void handingOffToAnIntermediateSiteMarksTripArrivedNotDelivered() {
        TripMaster master = new TripMaster(TRIP_ID, "depot", "destination", null);
        TripState state = new TripState(TRIP_ID, "AtOrigin", THIS_SITE, 1);
        when(tripMasterRepository.findById(TRIP_ID)).thenReturn(Optional.of(master));
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(state));

        HandoverResponse response = service.handOff(TRIP_ID, new HandoverRequest("port", "liaison-01"));

        assertThat(response.newStatus()).isEqualTo("Arrived");
        assertThat(response.lamportTs()).isEqualTo(2);
    }

    private static final UUID CONTAINER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();
    private static final UUID DRIVER_ID = UUID.randomUUID();

    private TripState tripWithAssignmentsHere() {
        TripMaster master = new TripMaster(TRIP_ID, "depot", "destination", null);
        TripState state = new TripState(TRIP_ID, "AtOrigin", THIS_SITE, 1);
        state.assignVehicle(VEHICLE_ID, 1);
        state.assignDriver(DRIVER_ID, 1);
        when(tripMasterRepository.findById(TRIP_ID)).thenReturn(Optional.of(master));
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(state));
        when(tripContainerRepository.findByTripId(TRIP_ID)).thenReturn(List.of(new TripContainer(TRIP_ID, CONTAINER_ID, THIS_SITE)));
        return state;
    }

    @Test
    void handingOffATripMovesItsContainersVehicleAndDriverWithIt() {
        tripWithAssignmentsHere();
        ContainerState container = new ContainerState(CONTAINER_ID, "AtOrigin", THIS_SITE, 2);
        VehicleAvailability vehicle = new VehicleAvailability(VEHICLE_ID, "Available", THIS_SITE, 3);
        DriverAvailability driver = new DriverAvailability(DRIVER_ID, "Available", THIS_SITE, 4);
        when(containerStateRepository.findById(CONTAINER_ID)).thenReturn(Optional.of(container));
        when(vehicleAvailabilityRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));
        when(driverAvailabilityRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));

        HandoverResponse response = service.handOff(TRIP_ID, new HandoverRequest("port", "liaison-01"));

        assertThat(response.containersMoved()).isEqualTo(1);
        assertThat(response.vehicleMoved()).isTrue();
        assertThat(response.driverMoved()).isTrue();
        assertThat(container.getCurrentSiteId()).isEqualTo("port");
        assertThat(container.getStatus()).isEqualTo("Arrived");
        assertThat(container.getLamportTs()).isEqualTo(3);
        assertThat(vehicle.getCurrentSiteId()).isEqualTo("port");
        assertThat(vehicle.getLamportTs()).isEqualTo(4);
        assertThat(driver.getCurrentSiteId()).isEqualTo("port");
        assertThat(driver.getLamportTs()).isEqualTo(5);
        verify(containerStateRepository).save(container);
        verify(vehicleAvailabilityRepository).save(vehicle);
        verify(driverAvailabilityRepository).save(driver);
    }

    @Test
    void refusesToHandOffWhenALinkedContainerIsHeldAtAnotherSiteAndMovesNothing() {
        TripState state = tripWithAssignmentsHere();
        ContainerState container = new ContainerState(CONTAINER_ID, "Arrived", "depot", 2);
        VehicleAvailability vehicle = new VehicleAvailability(VEHICLE_ID, "Available", THIS_SITE, 3);
        DriverAvailability driver = new DriverAvailability(DRIVER_ID, "Available", THIS_SITE, 4);
        when(containerStateRepository.findById(CONTAINER_ID)).thenReturn(Optional.of(container));
        when(vehicleAvailabilityRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));
        when(driverAvailabilityRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> service.handOff(TRIP_ID, new HandoverRequest("port", "liaison-01")))
                .isInstanceOf(InvalidHandoverException.class)
                .hasMessageContaining("container")
                .hasMessageContaining("depot");

        assertThat(state.getCurrentSiteId()).isEqualTo(THIS_SITE);
        assertThat(vehicle.getCurrentSiteId()).isEqualTo(THIS_SITE);
        verify(handoverRepository, never()).save(any(Handover.class));
        verify(tripStateRepository, never()).save(any());
        verify(containerStateRepository, never()).save(any());
    }

    @Test
    void refusesToHandOffWhenTheAssignedVehicleIsHeldAtAnotherSite() {
        tripWithAssignmentsHere();
        when(containerStateRepository.findById(CONTAINER_ID)).thenReturn(Optional.of(new ContainerState(CONTAINER_ID, "AtOrigin", THIS_SITE, 2)));
        when(vehicleAvailabilityRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(new VehicleAvailability(VEHICLE_ID, "Available", "port", 3)));
        when(driverAvailabilityRepository.findById(DRIVER_ID)).thenReturn(Optional.of(new DriverAvailability(DRIVER_ID, "Available", THIS_SITE, 4)));

        assertThatThrownBy(() -> service.handOff(TRIP_ID, new HandoverRequest("port", "liaison-01")))
                .isInstanceOf(InvalidHandoverException.class)
                .hasMessageContaining("vehicle");
        verify(handoverRepository, never()).save(any(Handover.class));
    }

    @Test
    void refusesToHandOffWhenTheAssignedDriverIsHeldAtAnotherSite() {
        tripWithAssignmentsHere();
        when(containerStateRepository.findById(CONTAINER_ID)).thenReturn(Optional.of(new ContainerState(CONTAINER_ID, "AtOrigin", THIS_SITE, 2)));
        when(vehicleAvailabilityRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(new VehicleAvailability(VEHICLE_ID, "Available", THIS_SITE, 3)));
        when(driverAvailabilityRepository.findById(DRIVER_ID)).thenReturn(Optional.of(new DriverAvailability(DRIVER_ID, "Available", "depot", 4)));

        assertThatThrownBy(() -> service.handOff(TRIP_ID, new HandoverRequest("port", "liaison-01")))
                .isInstanceOf(InvalidHandoverException.class)
                .hasMessageContaining("driver");
        verify(handoverRepository, never()).save(any(Handover.class));
    }
}
