package com.borderflow.handover;

import com.borderflow.common.InvalidHandoverException;
import com.borderflow.common.TripNotFoundException;
import com.borderflow.trip.TripMaster;
import com.borderflow.trip.TripMasterRepository;
import com.borderflow.trip.TripState;
import com.borderflow.trip.TripStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private HandoverService service;

    private static final UUID TRIP_ID = UUID.randomUUID();
    private static final String THIS_SITE = "border";

    @BeforeEach
    void setUp() {
        service = new HandoverService(tripMasterRepository, tripStateRepository, handoverRepository, THIS_SITE);
    }

    private TripMaster masterWithDestination(String destinationSiteId) throws Exception {
        TripMaster master = new TripMaster();
        setField(master, "tripId", TRIP_ID);
        setField(master, "destinationSiteId", destinationSiteId);
        return master;
    }

    private static void setField(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void rejectsHandoverWhenThisSiteDoesNotCurrentlyHoldTheTrip() {
        TripState state = new TripState(TRIP_ID, "Arrived", "port", 3); // held at "port", not "border"
        when(tripMasterRepository.findById(TRIP_ID)).thenReturn(Optional.of(new TripMaster()));
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service.handOff(TRIP_ID, new HandoverRequest("destination", "liaison-01")))
                .isInstanceOf(InvalidHandoverException.class)
                .hasMessageContaining("port")
                .hasMessageContaining("border");
    }

    @Test
    void rejectsHandoverOnAnAlreadyDeliveredTrip() {
        TripState state = new TripState(TRIP_ID, "Delivered", THIS_SITE, 4);
        when(tripMasterRepository.findById(TRIP_ID)).thenReturn(Optional.of(new TripMaster()));
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
    void handingOffToTheFinalDestinationMarksTripDelivered() throws Exception {
        TripMaster master = masterWithDestination("destination");
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
    void handingOffToAnIntermediateSiteMarksTripArrivedNotDelivered() throws Exception {
        TripMaster master = masterWithDestination("destination");
        TripState state = new TripState(TRIP_ID, "AtOrigin", THIS_SITE, 1);
        when(tripMasterRepository.findById(TRIP_ID)).thenReturn(Optional.of(master));
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(state));

        HandoverResponse response = service.handOff(TRIP_ID, new HandoverRequest("port", "liaison-01"));

        assertThat(response.newStatus()).isEqualTo("Arrived");
        assertThat(response.lamportTs()).isEqualTo(2);
    }
}
