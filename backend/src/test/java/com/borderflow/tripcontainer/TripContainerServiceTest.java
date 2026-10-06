package com.borderflow.tripcontainer;

import com.borderflow.common.InvalidTripContainerLinkException;
import com.borderflow.container.ContainerMasterRepository;
import com.borderflow.container.ContainerState;
import com.borderflow.container.ContainerStateRepository;
import com.borderflow.trip.TripMasterRepository;
import com.borderflow.trip.TripState;
import com.borderflow.trip.TripStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripContainerServiceTest {

    @Mock
    private TripMasterRepository tripMasterRepository;
    @Mock
    private TripStateRepository tripStateRepository;
    @Mock
    private ContainerMasterRepository containerMasterRepository;
    @Mock
    private ContainerStateRepository containerStateRepository;
    @Mock
    private TripContainerRepository tripContainerRepository;

    private static final UUID TRIP_ID = UUID.randomUUID();
    private static final UUID CONTAINER_ID = UUID.randomUUID();

    @Test
    void linkSucceedsWhenBothAreHeldAtThisSite() {
        TripContainerService service = new TripContainerService(
                tripMasterRepository, tripStateRepository, containerMasterRepository, containerStateRepository,
                tripContainerRepository, "depot");

        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(new TripState(TRIP_ID, "AtOrigin", "depot", 1)));
        when(containerStateRepository.findById(CONTAINER_ID)).thenReturn(Optional.of(new ContainerState(CONTAINER_ID, "AtOrigin", "depot", 1)));

        service.link(TRIP_ID, CONTAINER_ID);

        verify(tripContainerRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void linkIsRejectedWhenTripIsAtADifferentSiteThanThisOne() {
        TripContainerService service = new TripContainerService(
                tripMasterRepository, tripStateRepository, containerMasterRepository, containerStateRepository,
                tripContainerRepository, "depot");

        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(new TripState(TRIP_ID, "Arrived", "border", 2)));
        when(containerStateRepository.findById(CONTAINER_ID)).thenReturn(Optional.of(new ContainerState(CONTAINER_ID, "AtOrigin", "depot", 1)));

        assertThatThrownBy(() -> service.link(TRIP_ID, CONTAINER_ID))
                .isInstanceOf(InvalidTripContainerLinkException.class)
                .hasMessageContaining("border");
    }

    @Test
    void linkIsRejectedWhenContainerIsAtADifferentSiteThanThisOne() {
        TripContainerService service = new TripContainerService(
                tripMasterRepository, tripStateRepository, containerMasterRepository, containerStateRepository,
                tripContainerRepository, "depot");

        when(tripStateRepository.findById(TRIP_ID)).thenReturn(Optional.of(new TripState(TRIP_ID, "AtOrigin", "depot", 1)));
        when(containerStateRepository.findById(CONTAINER_ID)).thenReturn(Optional.of(new ContainerState(CONTAINER_ID, "Arrived", "port", 2)));

        assertThatThrownBy(() -> service.link(TRIP_ID, CONTAINER_ID))
                .isInstanceOf(InvalidTripContainerLinkException.class)
                .hasMessageContaining("port");
    }
}
