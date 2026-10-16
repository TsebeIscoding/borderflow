package com.borderflow.client;

import com.borderflow.common.ClientNotFoundException;
import com.borderflow.common.DeliveryPointNotFoundException;
import com.borderflow.common.InvalidConsignmentDetailsException;
import com.borderflow.common.OriginSiteOnlyException;
import com.borderflow.deliverypoint.DeliveryPointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsignmentCreationServiceTest {

    @Mock
    private ConsignmentRepository consignmentRepository;
    @Mock
    private ClientCoreRepository clientCoreRepository;
    @Mock
    private DeliveryPointRepository deliveryPointRepository;

    private ConsignmentCreationService serviceAt(String siteId) {
        return new ConsignmentCreationService(consignmentRepository, clientCoreRepository, deliveryPointRepository, siteId);
    }

    /** A valid request; individual tests vary the optional parts by passing different values. */
    private static ConsignmentCreateRequest request(UUID clientId, UUID deliveryPointId, BigDecimal declaredValue, String currency, String customsReference) {
        return new ConsignmentCreateRequest(
                clientId, "Test consignment", deliveryPointId,
                "General", new BigDecimal("1250.50"), 12,
                declaredValue, currency, customsReference,
                "  Keep dry  ", "Warehouse 4, Industrial Area", LocalDate.of(2026, 12, 1));
    }

    @Test
    void createSucceedsAtDepotAndIssuesAReferenceNumber() {
        ConsignmentCreationService service = serviceAt("depot");
        UUID clientId = UUID.randomUUID();
        UUID deliveryPointId = UUID.randomUUID();
        when(clientCoreRepository.existsById(clientId)).thenReturn(true);
        when(deliveryPointRepository.existsById(deliveryPointId)).thenReturn(true);
        when(consignmentRepository.nextReferenceSequence()).thenReturn(7L);

        ConsignmentSummaryResponse response = service.create(
                request(clientId, deliveryPointId, new BigDecimal("12000.00"), "LSL", "CUS-001"));

        assertThat(response.clientId()).isEqualTo(clientId);
        assertThat(response.deliveryPointId()).isEqualTo(deliveryPointId);
        assertThat(response.referenceNumber()).isEqualTo("CON-" + LocalDate.now().getYear() + "-0007");
        assertThat(response.cargoType()).isEqualTo("General");
        assertThat(response.totalWeightKg()).isEqualByComparingTo("1250.50");
        assertThat(response.packageCount()).isEqualTo(12);
        assertThat(response.declaredValue()).isEqualByComparingTo("12000.00");
        assertThat(response.currency()).isEqualTo("LSL");
        assertThat(response.customsReference()).isEqualTo("CUS-001");
        assertThat(response.handlingNotes()).isEqualTo("Keep dry");
        assertThat(response.deliveryLocation()).isEqualTo("Warehouse 4, Industrial Area");
        assertThat(response.expectedDeliveryDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        verify(consignmentRepository).save(ArgumentMatchers.any());
    }

    @Test
    void blankOptionalTextFieldsAreStoredAsNull() {
        ConsignmentCreationService service = serviceAt("depot");
        UUID clientId = UUID.randomUUID();
        UUID deliveryPointId = UUID.randomUUID();
        when(clientCoreRepository.existsById(clientId)).thenReturn(true);
        when(deliveryPointRepository.existsById(deliveryPointId)).thenReturn(true);
        when(consignmentRepository.nextReferenceSequence()).thenReturn(1L);

        ConsignmentSummaryResponse response = service.create(
                new ConsignmentCreateRequest(clientId, "desc", deliveryPointId, "Bulk", new BigDecimal("10"), 1,
                        null, null, "   ", "", null, null));

        assertThat(response.customsReference()).isNull();
        assertThat(response.handlingNotes()).isNull();
        assertThat(response.deliveryLocation()).isNull();
        assertThat(response.declaredValue()).isNull();
        assertThat(response.currency()).isNull();
    }

    @Test
    void declaredValueWithoutCurrencyIsRejectedAndUsesNoReferenceNumber() {
        ConsignmentCreationService service = serviceAt("depot");
        UUID clientId = UUID.randomUUID();
        UUID deliveryPointId = UUID.randomUUID();
        when(clientCoreRepository.existsById(clientId)).thenReturn(true);
        when(deliveryPointRepository.existsById(deliveryPointId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(clientId, deliveryPointId, new BigDecimal("500"), null, null)))
                .isInstanceOf(InvalidConsignmentDetailsException.class)
                .hasMessageContaining("together");
        verify(consignmentRepository, never()).nextReferenceSequence();
    }

    @Test
    void currencyWithoutDeclaredValueIsRejected() {
        ConsignmentCreationService service = serviceAt("depot");
        UUID clientId = UUID.randomUUID();
        UUID deliveryPointId = UUID.randomUUID();
        when(clientCoreRepository.existsById(clientId)).thenReturn(true);
        when(deliveryPointRepository.existsById(deliveryPointId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(clientId, deliveryPointId, null, "LSL", null)))
                .isInstanceOf(InvalidConsignmentDetailsException.class);
    }

    @Test
    void createFailsCleanlyWhenReferencedClientDoesNotExist() {
        ConsignmentCreationService service = serviceAt("depot");
        UUID clientId = UUID.randomUUID();
        when(clientCoreRepository.existsById(clientId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(clientId, UUID.randomUUID(), null, null, null)))
                .isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    void createFailsCleanlyWhenDeliveryPointDoesNotExist() {
        ConsignmentCreationService service = serviceAt("depot");
        UUID clientId = UUID.randomUUID();
        UUID deliveryPointId = UUID.randomUUID();
        when(clientCoreRepository.existsById(clientId)).thenReturn(true);
        when(deliveryPointRepository.existsById(deliveryPointId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(clientId, deliveryPointId, null, null, null)))
                .isInstanceOf(DeliveryPointNotFoundException.class);
    }

    @Test
    void createIsRejectedAtNonOriginSites() {
        ConsignmentCreationService service = serviceAt("border");

        assertThatThrownBy(() -> service.create(request(UUID.randomUUID(), UUID.randomUUID(), null, null, null)))
                .isInstanceOf(OriginSiteOnlyException.class);
    }

    @Test
    void deleteIsRejectedAtNonOriginSites() {
        ConsignmentCreationService service = serviceAt("port");

        assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
                .isInstanceOf(OriginSiteOnlyException.class);
    }
}
