package com.borderflow.container;

import com.borderflow.client.ConsignmentRepository;
import com.borderflow.common.ConsignmentNotFoundException;
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
import static org.mockito.Mockito.when;

/** Same shape as TripCreationServiceTest. */
@ExtendWith(MockitoExtension.class)
class ContainerCreationServiceTest {

    @Mock
    private ContainerMasterRepository containerMasterRepository;
    @Mock
    private ContainerStateRepository containerStateRepository;
    @Mock
    private ConsignmentRepository consignmentRepository;

    @Test
    void createSucceedsAtDepot() {
        ContainerCreationService service = new ContainerCreationService(containerMasterRepository, containerStateRepository, consignmentRepository, "depot");

        UUID consignmentId = UUID.randomUUID();
        when(consignmentRepository.existsById(consignmentId)).thenReturn(true);

        ContainerSummaryResponse response = service.create(new ContainerCreateRequest("CONT-0001", consignmentId, "40ft"));

        assertThat(response.containerNumber()).isEqualTo("CONT-0001");
        assertThat(response.currentSiteId()).isEqualTo("depot");
        assertThat(response.status()).isEqualTo("AtOrigin");
        assertThat(response.lamportTs()).isEqualTo(1);
        verify(containerMasterRepository).save(ArgumentMatchers.any());
        verify(containerStateRepository).save(ArgumentMatchers.any());
    }

    @Test
    void createFailsCleanlyWhenReferencedConsignmentDoesNotExist() {
        ContainerCreationService service = new ContainerCreationService(containerMasterRepository, containerStateRepository, consignmentRepository, "depot");
        UUID consignmentId = UUID.randomUUID();
        when(consignmentRepository.existsById(consignmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new ContainerCreateRequest("CONT-0003", consignmentId, "40ft")))
                .isInstanceOf(ConsignmentNotFoundException.class);
    }

    @Test
    void createIsRejectedAtNonOriginSites() {
        ContainerCreationService service = new ContainerCreationService(containerMasterRepository, containerStateRepository, consignmentRepository, "port");

        assertThatThrownBy(() -> service.create(new ContainerCreateRequest("CONT-0002", UUID.randomUUID(), "20ft")))
                .isInstanceOf(OriginSiteOnlyException.class);
    }

    @Test
    void deleteIsRejectedAtNonOriginSites() {
        ContainerCreationService service = new ContainerCreationService(containerMasterRepository, containerStateRepository, consignmentRepository, "destination");

        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(OriginSiteOnlyException.class);
    }
}
