package com.borderflow.incident;

import com.borderflow.common.InvalidHandoverException;
import com.borderflow.common.TripNotFoundException;
import com.borderflow.trip.TripState;
import com.borderflow.trip.TripStateRepository;
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

/** Same shape as MilestoneServiceTest. */
@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private TripStateRepository tripStateRepository;

    private static final UUID TRIP_ID = UUID.randomUUID();

    @Test
    void recordSucceedsWhenThisSiteHoldsTheTrip() {
        IncidentService service = new IncidentService(incidentRepository, tripStateRepository, "port");
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(new TripState(TRIP_ID, "Arrived", "port", 3)));

        IncidentResponse response = service.record(TRIP_ID, new IncidentCreateRequest("Delayed at customs"));

        assertThat(response.description()).isEqualTo("Delayed at customs");
        assertThat(response.siteId()).isEqualTo("port");
        verify(incidentRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordIsRejectedWhenThisSiteDoesNotHoldTheTrip() {
        IncidentService service = new IncidentService(incidentRepository, tripStateRepository, "depot");
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(new TripState(TRIP_ID, "Arrived", "port", 3)));

        assertThatThrownBy(() -> service.record(TRIP_ID, new IncidentCreateRequest("Delayed at customs")))
                .isInstanceOf(InvalidHandoverException.class);
    }

    @Test
    void recordThrowsTripNotFoundWhenTripDoesNotExist() {
        IncidentService service = new IncidentService(incidentRepository, tripStateRepository, "depot");
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(TRIP_ID, new IncidentCreateRequest("Delayed at customs")))
                .isInstanceOf(TripNotFoundException.class);
    }
}
