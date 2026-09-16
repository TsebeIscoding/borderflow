package com.borderflow.container;

import com.borderflow.common.ContainerNotFoundException;
import com.borderflow.common.InvalidContainerMoveException;
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

/**
 * Mirrors HandoverServiceTest's shape. Only one business rule exists
 * here (site-holds-container) -- there's no "terminal status" test
 * like HandoverServiceTest's Delivered case, since container_master
 * has no destination_site_id (see ContainerRelocationService's class
 * javadoc for why).
 */
@ExtendWith(MockitoExtension.class)
class ContainerRelocationServiceTest {

    @Mock
    private ContainerMasterRepository containerMasterRepository;
    @Mock
    private ContainerStateRepository containerStateRepository;

    private ContainerRelocationService service;

    private static final UUID CONTAINER_ID = UUID.randomUUID();
    private static final String THIS_SITE = "border";

    @BeforeEach
    void setUp() {
        service = new ContainerRelocationService(containerMasterRepository, containerStateRepository, THIS_SITE);
    }

    @Test
    void rejectsRelocationWhenThisSiteDoesNotCurrentlyHoldTheContainer() {
        ContainerState state = new ContainerState(CONTAINER_ID, "Arrived", "port", 3);
        when(containerMasterRepository.findById(CONTAINER_ID)).thenReturn(Optional.of(new ContainerMaster()));
        when(containerStateRepository.findById(CONTAINER_ID)).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service.relocate(CONTAINER_ID, new ContainerRelocationRequest("destination", "liaison-01")))
                .isInstanceOf(InvalidContainerMoveException.class)
                .hasMessageContaining("port")
                .hasMessageContaining("border");
    }

    @Test
    void throwsContainerNotFoundWhenMasterIsMissing() {
        when(containerMasterRepository.findById(CONTAINER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.relocate(CONTAINER_ID, new ContainerRelocationRequest("port", "liaison-01")))
                .isInstanceOf(ContainerNotFoundException.class);
    }

    @Test
    void successfulRelocationAdvancesStateAndLamportTimestamp() {
        ContainerState state = new ContainerState(CONTAINER_ID, "AtOrigin", THIS_SITE, 1);
        when(containerMasterRepository.findById(CONTAINER_ID)).thenReturn(Optional.of(new ContainerMaster()));
        when(containerStateRepository.findById(CONTAINER_ID)).thenReturn(Optional.of(state));

        ContainerRelocationResponse response = service.relocate(CONTAINER_ID, new ContainerRelocationRequest("port", "liaison-01"));

        assertThat(response.newStatus()).isEqualTo("Arrived");
        assertThat(response.lamportTs()).isEqualTo(2);
        assertThat(response.fromSiteId()).isEqualTo(THIS_SITE);
        assertThat(response.toSiteId()).isEqualTo("port");
        verify(containerStateRepository).save(state);
    }
}
