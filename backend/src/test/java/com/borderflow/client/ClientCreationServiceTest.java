package com.borderflow.client;

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

@ExtendWith(MockitoExtension.class)
class ClientCreationServiceTest {

    @Mock
    private ClientCoreRepository clientCoreRepository;

    @Test
    void createSucceedsAtDepot() {
        ClientCreationService service = new ClientCreationService(clientCoreRepository, "depot");

        ClientSummaryResponse response = service.create(new ClientCreateRequest("Test Client Co"));

        assertThat(response.name()).isEqualTo("Test Client Co");
        verify(clientCoreRepository).save(ArgumentMatchers.any());
    }

    @Test
    void createIsRejectedAtNonOriginSites() {
        ClientCreationService service = new ClientCreationService(clientCoreRepository, "border");

        assertThatThrownBy(() -> service.create(new ClientCreateRequest("Another Client")))
                .isInstanceOf(OriginSiteOnlyException.class);
    }

    @Test
    void deleteIsRejectedAtNonOriginSites() {
        ClientCreationService service = new ClientCreationService(clientCoreRepository, "port");

        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(OriginSiteOnlyException.class);
    }
}
