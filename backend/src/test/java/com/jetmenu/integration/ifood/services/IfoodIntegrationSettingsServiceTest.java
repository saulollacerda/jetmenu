package com.jetmenu.integration.ifood.services;

import com.jetmenu.integration.ifood.IfoodIntegrationStatus;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("IfoodIntegrationSettingsService")
class IfoodIntegrationSettingsServiceTest {

    @Mock private MerchantRepository merchantRepository;

    @InjectMocks
    private IfoodIntegrationSettingsService service;

    private UUID merchantId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        merchant = Merchant.builder().id(merchantId).build();
    }

    @Test
    @DisplayName("getStatus retorna estado completo para merchant conectado")
    void getStatus_shouldReturnFullStateForConnectedMerchant() {
        LocalDateTime importedAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        merchant.setIfoodMerchantId("ifood-m1");
        merchant.setIfoodCatalogImportedAt(importedAt);
        merchant.setIfoodOrderSyncEnabled(true);
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

        IfoodIntegrationStatus status = service.getStatus(merchantId);

        assertThat(status.connected()).isTrue();
        assertThat(status.catalogImportedAt()).isEqualTo(importedAt);
        assertThat(status.orderSyncEnabled()).isTrue();
    }

    @Test
    @DisplayName("getStatus retorna tudo desligado para merchant desconectado")
    void getStatus_shouldReturnDisconnectedState() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

        IfoodIntegrationStatus status = service.getStatus(merchantId);

        assertThat(status.connected()).isFalse();
        assertThat(status.catalogImportedAt()).isNull();
        assertThat(status.orderSyncEnabled()).isFalse();
    }

    @Test
    @DisplayName("getStatus retorna desconectado para merchant inexistente")
    void getStatus_shouldReturnDisconnectedForUnknownMerchant() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.empty());

        IfoodIntegrationStatus status = service.getStatus(merchantId);

        assertThat(status.connected()).isFalse();
        assertThat(status.catalogImportedAt()).isNull();
        assertThat(status.orderSyncEnabled()).isFalse();
    }

    @Test
    @DisplayName("setOrderSyncEnabled persiste o flag e retorna o status atualizado")
    void setOrderSyncEnabled_shouldPersistFlag() {
        merchant.setIfoodMerchantId("ifood-m1");
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

        IfoodIntegrationStatus status = service.setOrderSyncEnabled(merchantId, true);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).save(captor.capture());
        assertThat(captor.getValue().isIfoodOrderSyncEnabled()).isTrue();
        assertThat(status.connected()).isTrue();
        assertThat(status.orderSyncEnabled()).isTrue();
    }

    @Test
    @DisplayName("setOrderSyncEnabled(false) desativa a sincronia")
    void setOrderSyncEnabled_shouldDisableFlag() {
        merchant.setIfoodMerchantId("ifood-m1");
        merchant.setIfoodOrderSyncEnabled(true);
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

        IfoodIntegrationStatus status = service.setOrderSyncEnabled(merchantId, false);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).save(captor.capture());
        assertThat(captor.getValue().isIfoodOrderSyncEnabled()).isFalse();
        assertThat(status.orderSyncEnabled()).isFalse();
    }

    @Test
    @DisplayName("setOrderSyncEnabled lança IllegalStateException quando o merchant não está conectado")
    void setOrderSyncEnabled_shouldThrowWhenNotConnected() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

        assertThatThrownBy(() -> service.setOrderSyncEnabled(merchantId, true))
                .isInstanceOf(IllegalStateException.class);
        then(merchantRepository).should(never()).save(merchant);
    }

    @Test
    @DisplayName("setOrderSyncEnabled lança IllegalStateException quando o merchant não existe")
    void setOrderSyncEnabled_shouldThrowWhenMerchantNotFound() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.setOrderSyncEnabled(merchantId, true))
                .isInstanceOf(IllegalStateException.class);
    }
}
