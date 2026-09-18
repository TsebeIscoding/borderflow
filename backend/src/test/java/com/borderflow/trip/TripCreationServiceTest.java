package com.borderflow.trip;

import com.borderflow.common.OriginSiteOnlyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * Only tests the one piece of logic worth testing here: the
 * origin-site restriction. Everything else (the actual INSERT) is
 * exercised against the real database in the manual verification pass
 * documented in docs/testing/test-results.md, same as every other
 * *CreationService.
 */
@ExtendWith(MockitoExtension.class)
class TripCreationServiceTest {

    @Mock
    private TripMasterRepository tripMasterRepository;
    @Mock
    private TripStateRepository tripStateRepository;

    @Test
    void createSucceedsAtDepot() {
        TripCreationService service = new TripCreationService(tripMasterRepository, tripStateRepository, "depot");

        TripSummaryResponse response = service.create(new TripCreateRequest("border"));

        assertThat(response.originSiteId()).isEqualTo("depot");
        assertThat(response.destinationSiteId()).isEqualTo("border");
        assertThat(response.status()).isEqualTo("AtOrigin");
        assertThat(response.lamportTs()).isEqualTo(1);
        verify(tripMasterRepository).save(org.mockito.ArgumentMatchers.any());
        verify(tripStateRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createIsRejectedAtNonOriginSites() {
        TripCreationService service = new TripCreationService(tripMasterRepository, tripStateRepository, "border");

        assertThatThrownBy(() -> service.create(new TripCreateRequest("port")))
                .isInstanceOf(OriginSiteOnlyException.class)
                .hasMessageContaining("depot")
                .hasMessageContaining("border");
    }

    @Test
    void deleteIsRejectedAtNonOriginSites() {
        TripCreationService service = new TripCreationService(tripMasterRepository, tripStateRepository, "port");

        assertThatThrownBy(() -> service.delete(java.util.UUID.randomUUID()))
                .isInstanceOf(OriginSiteOnlyException.class);
    }
}
