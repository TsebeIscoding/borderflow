package com.borderflow.container;

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

/** Same shape as TripCreationServiceTest. */
@ExtendWith(MockitoExtension.class)
class ContainerCreationServiceTest {

    @Mock
    private ContainerMasterRepository containerMasterRepository;
    @Mock
    private ContainerStateRepository containerStateRepository;

    @Test
    void createSucceedsAtDepot() {
        ContainerCreationService service = new ContainerCreationService(containerMasterRepository, containerStateRepository, "depot");

        ContainerSummaryResponse response = service.create(new ContainerCreateRequest("CONT-0001", UUID.randomUUID(), "40ft"));

        assertThat(response.containerNumber()).isEqualTo("CONT-0001");
        assertThat(response.currentSiteId()).isEqualTo("depot");
        assertThat(response.status()).isEqualTo("AtOrigin");
        assertThat(response.lamportTs()).isEqualTo(1);
        verify(containerMasterRepository).save(ArgumentMatchers.any());
        verify(containerStateRepository).save(ArgumentMatchers.any());
    }

    @Test
    void createIsRejectedAtNonOriginSites() {
        ContainerCreationService service = new ContainerCreationService(containerMasterRepository, containerStateRepository, "port");

        assertThatThrownBy(() -> service.create(new ContainerCreateRequest("CONT-0002", UUID.randomUUID(), "20ft")))
                .isInstanceOf(OriginSiteOnlyException.class);
    }

    @Test
    void deleteIsRejectedAtNonOriginSites() {
        ContainerCreationService service = new ContainerCreationService(containerMasterRepository, containerStateRepository, "destination");

        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(OriginSiteOnlyException.class);
    }
}
