package com.borderflow.milestone;

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

@ExtendWith(MockitoExtension.class)
class MilestoneServiceTest {

    @Mock
    private MilestoneRepository milestoneRepository;
    @Mock
    private TripStateRepository tripStateRepository;

    private static final UUID TRIP_ID = UUID.randomUUID();

    @Test
    void recordSucceedsWhenThisSiteHoldsTheTrip() {
        MilestoneService service = new MilestoneService(milestoneRepository, tripStateRepository, "border");
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(new TripState(TRIP_ID, "Arrived", "border", 2)));

        MilestoneResponse response = service.record(TRIP_ID, new MilestoneCreateRequest("CustomsCleared"));

        assertThat(response.milestoneType()).isEqualTo("CustomsCleared");
        assertThat(response.siteId()).isEqualTo("border");
        verify(milestoneRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordIsRejectedWhenThisSiteDoesNotHoldTheTrip() {
        MilestoneService service = new MilestoneService(milestoneRepository, tripStateRepository, "depot");
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(new TripState(TRIP_ID, "Arrived", "border", 2)));

        assertThatThrownBy(() -> service.record(TRIP_ID, new MilestoneCreateRequest("CustomsCleared")))
                .isInstanceOf(InvalidHandoverException.class)
                .hasMessageContaining("border")
                .hasMessageContaining("depot");
    }

    @Test
    void recordThrowsTripNotFoundWhenTripDoesNotExist() {
        MilestoneService service = new MilestoneService(milestoneRepository, tripStateRepository, "depot");
        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(TRIP_ID, new MilestoneCreateRequest("CustomsCleared")))
                .isInstanceOf(TripNotFoundException.class);
    }
}
