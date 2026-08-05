package com.jetmenu.merchant;

import com.jetmenu.billing.SubscriptionService;
import com.jetmenu.common.ForbiddenException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantService")
class MerchantServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private MerchantService merchantService;

    private UUID merchantId;
    private Merchant merchant;
    private MerchantRequest merchantRequest;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();

        merchantRequest = MerchantRequest.builder()
                .merchantName("Restaurante Teste")
                .cnpj("12345678000195")
                .email("teste@email.com")
                .password("senha123")
                .confirmPassword("senha123")
                .phone("11999999999")
                .build();

        merchant = Merchant.builder()
                .id(merchantId)
                .merchantName("Restaurante Teste")
                .cnpj("12345678000195")
                .email("teste@email.com")
                .password("$2a$10$encodedpassword")
                .phone("11999999999")
                .status(MerchantStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("deve criar usuário com dados válidos e retornar MerchantResponse")
        void shouldCreateUserAndReturnMerchantResponse() {
            given(merchantRepository.existsByEmail(merchantRequest.getEmail())).willReturn(false);
            given(merchantRepository.existsByCnpj(merchantRequest.getCnpj())).willReturn(false);
            given(passwordEncoder.encode(merchantRequest.getPassword())).willReturn("$2a$10$encodedpassword");
            given(merchantRepository.save(any(Merchant.class))).willReturn(merchant);

            MerchantResponse result = merchantService.create(merchantRequest);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(merchantRequest.getEmail());
            assertThat(result.getMerchantName()).isEqualTo(merchantRequest.getMerchantName());
            assertThat(result.getCnpj()).isEqualTo(merchantRequest.getCnpj());
            then(merchantRepository).should().save(any(Merchant.class));
        }

        @Test
        @DisplayName("deve criar usuário com status ACTIVE por padrão")
        void shouldCreateUserWithActiveStatusByDefault() {
            given(merchantRepository.existsByEmail(anyString())).willReturn(false);
            given(merchantRepository.existsByCnpj(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("$2a$10$encodedpassword");
            given(merchantRepository.save(any(Merchant.class))).willReturn(merchant);

            MerchantResponse result = merchantService.create(merchantRequest);

            assertThat(result.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
        }

        @Test
        @DisplayName("deve encriptar a senha antes de salvar")
        void shouldEncryptPasswordBeforeSaving() {
            given(merchantRepository.existsByEmail(anyString())).willReturn(false);
            given(merchantRepository.existsByCnpj(anyString())).willReturn(false);
            given(passwordEncoder.encode(merchantRequest.getPassword())).willReturn("$2a$10$encodedpassword");
            given(merchantRepository.save(any(Merchant.class))).willReturn(merchant);

            merchantService.create(merchantRequest);

            then(passwordEncoder).should().encode(merchantRequest.getPassword());
            then(merchantRepository).should().save(argThat(u ->
                    !u.getPassword().equals(merchantRequest.getPassword())
            ));
        }

        @Test
        @DisplayName("deve criar uma subscription PENDING para o merchant recém-criado")
        void shouldCreatePendingSubscriptionForNewMerchant() {
            given(merchantRepository.existsByEmail(anyString())).willReturn(false);
            given(merchantRepository.existsByCnpj(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("$2a$10$encodedpassword");
            given(merchantRepository.save(any(Merchant.class))).willReturn(merchant);

            merchantService.create(merchantRequest);

            then(subscriptionService).should().createPendingSubscription(merchant.getId());
        }

        @Test
        @DisplayName("deve lançar DuplicateMerchantException quando email já está cadastrado")
        void shouldThrowWhenEmailAlreadyExists() {
            given(merchantRepository.existsByEmail(merchantRequest.getEmail())).willReturn(true);

            assertThatThrownBy(() -> merchantService.create(merchantRequest))
                    .isInstanceOf(DuplicateMerchantException.class)
                    .hasMessageContaining("email");

            then(merchantRepository).should(never()).save(any(Merchant.class));
        }

        @Test
        @DisplayName("deve lançar DuplicateMerchantException quando CNPJ já está cadastrado")
        void shouldThrowWhenCnpjAlreadyExists() {
            given(merchantRepository.existsByEmail(merchantRequest.getEmail())).willReturn(false);
            given(merchantRepository.existsByCnpj(merchantRequest.getCnpj())).willReturn(true);

            assertThatThrownBy(() -> merchantService.create(merchantRequest))
                    .isInstanceOf(DuplicateMerchantException.class)
                    .hasMessageContaining("CNPJ");

            then(merchantRepository).should(never()).save(any(Merchant.class));
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("deve retornar MerchantResponse quando o usuário é o próprio")
        void shouldReturnMerchantResponseWhenOwner() {
            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

            MerchantResponse result = merchantService.findById(merchantId, merchantId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(merchantId);
        }

        @Test
        @DisplayName("deve lançar ForbiddenException ao tentar ler outro usuário")
        void shouldThrowForbiddenWhenAccessingAnotherUser() {
            assertThatThrownBy(() -> merchantService.findById(UUID.randomUUID(), merchantId))
                    .isInstanceOf(ForbiddenException.class);

            then(merchantRepository).should(never()).findById(any());
        }

        @Test
        @DisplayName("deve lançar MerchantNotFoundException quando usuário não existe")
        void shouldThrowWhenUserNotFound() {
            given(merchantRepository.findById(merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> merchantService.findById(merchantId, merchantId))
                    .isInstanceOf(MerchantNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("deve atualizar o próprio usuário e retornar MerchantResponse atualizado")
        void shouldUpdateAndReturnUpdatedMerchantResponse() {
            MerchantRequest updateRequest = MerchantRequest.builder()
                    .merchantName("Restaurante Atualizado")
                    .cnpj("12345678000195")
                    .email("teste@email.com")
                    .password("novaSenha123")
                    .confirmPassword("novaSenha123")
                    .phone("11988888888")
                    .build();

            Merchant updatedUser = Merchant.builder()
                    .id(merchantId)
                    .merchantName("Restaurante Atualizado")
                    .cnpj("12345678000195")
                    .email("teste@email.com")
                    .password("$2a$10$newencoded")
                    .phone("11988888888")
                    .status(MerchantStatus.ACTIVE)
                    .createdAt(merchant.getCreatedAt())
                    .build();

            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
            given(passwordEncoder.encode(updateRequest.getPassword())).willReturn("$2a$10$newencoded");
            given(merchantRepository.save(any(Merchant.class))).willReturn(updatedUser);

            MerchantResponse result = merchantService.update(merchantId, merchantId, updateRequest);

            assertThat(result.getMerchantName()).isEqualTo("Restaurante Atualizado");
            assertThat(result.getPhone()).isEqualTo("11988888888");
        }

        @Test
        @DisplayName("deve lançar ForbiddenException ao tentar atualizar outro usuário")
        void shouldThrowForbiddenWhenUpdatingAnotherUser() {
            assertThatThrownBy(() -> merchantService.update(UUID.randomUUID(), merchantId, merchantRequest))
                    .isInstanceOf(ForbiddenException.class);

            then(merchantRepository).should(never()).save(any(Merchant.class));
        }

        @Test
        @DisplayName("deve lançar MerchantNotFoundException ao atualizar usuário inexistente")
        void shouldThrowWhenUserNotFoundForUpdate() {
            given(merchantRepository.findById(merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> merchantService.update(merchantId, merchantId, merchantRequest))
                    .isInstanceOf(MerchantNotFoundException.class);

            then(merchantRepository).should(never()).save(any(Merchant.class));
        }
    }

    @Nested
    @DisplayName("updateAnotaAIKey()")
    class UpdateAnotaAIKey {

        @Test
        @DisplayName("deve salvar a chave do Anota.AI no usuário autenticado")
        void shouldSaveKeyOnCurrentUser() {
            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
            given(merchantRepository.save(any(Merchant.class))).willAnswer(inv -> inv.getArgument(0));

            MerchantResponse result = merchantService.updateAnotaAIKey(merchantId, new AnotaAIKeyRequest("my-key"));

            assertThat(result.getAnotaAiApiKey()).isEqualTo("my-key");
            assertThat(merchant.getAnotaAiApiKey()).isEqualTo("my-key");
            then(passwordEncoder).should(never()).encode(anyString());
        }

        @Test
        @DisplayName("deve aceitar chave nula (remover a chave)")
        void shouldAcceptNullKey() {
            merchant.setAnotaAiApiKey("old-key");
            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
            given(merchantRepository.save(any(Merchant.class))).willAnswer(inv -> inv.getArgument(0));

            MerchantResponse result = merchantService.updateAnotaAIKey(merchantId, new AnotaAIKeyRequest(null));

            assertThat(result.getAnotaAiApiKey()).isNull();
            assertThat(merchant.getAnotaAiApiKey()).isNull();
        }

        @Test
        @DisplayName("deve lançar MerchantNotFoundException se usuário autenticado não existir")
        void shouldThrowIfUserNotFound() {
            given(merchantRepository.findById(merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> merchantService.updateAnotaAIKey(merchantId, new AnotaAIKeyRequest("k")))
                    .isInstanceOf(MerchantNotFoundException.class);

            then(merchantRepository).should(never()).save(any(Merchant.class));
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("deve deletar o próprio usuário sem lançar exceção")
        void shouldDeleteOwnUser() {
            given(merchantRepository.existsById(merchantId)).willReturn(true);
            willDoNothing().given(merchantRepository).deleteById(merchantId);

            assertThatNoException().isThrownBy(() -> merchantService.delete(merchantId, merchantId));

            then(merchantRepository).should().deleteById(merchantId);
        }

        @Test
        @DisplayName("deve lançar ForbiddenException ao tentar deletar outro usuário")
        void shouldThrowForbiddenWhenDeletingAnotherUser() {
            assertThatThrownBy(() -> merchantService.delete(UUID.randomUUID(), merchantId))
                    .isInstanceOf(ForbiddenException.class);

            then(merchantRepository).should(never()).deleteById(any());
        }

        @Test
        @DisplayName("deve lançar MerchantNotFoundException ao deletar usuário inexistente")
        void shouldThrowWhenUserNotFoundForDelete() {
            given(merchantRepository.existsById(merchantId)).willReturn(false);

            assertThatThrownBy(() -> merchantService.delete(merchantId, merchantId))
                    .isInstanceOf(MerchantNotFoundException.class);

            then(merchantRepository).should(never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("findMe() / updateMe()")
    class Me {

        @Test
        @DisplayName("findMe deve retornar o merchant autenticado pelo contexto")
        void findMeShouldReturnAuthenticated() {
            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

            MerchantResponse result = merchantService.findMe(merchantId);

            assertThat(result.getId()).isEqualTo(merchantId);
        }

        @Test
        @DisplayName("updateMe deve aplicar apenas campos não-null e ignorar cnpj/email")
        void updateMeShouldApplyOnlyNonNullFields() {
            MerchantUpdateRequest req = MerchantUpdateRequest.builder()
                    .merchantName("Novo Nome")
                    .address("Rua A, 100")
                    .phone(null)
                    .build();

            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
            given(merchantRepository.save(any(Merchant.class))).willAnswer(inv -> inv.getArgument(0));

            MerchantResponse result = merchantService.updateMe(merchantId, req);

            assertThat(result.getMerchantName()).isEqualTo("Novo Nome");
            assertThat(result.getAddress()).isEqualTo("Rua A, 100");
            // phone permanece (request enviou null)
            assertThat(result.getPhone()).isEqualTo("11999999999");
            // cnpj imutável
            assertThat(result.getCnpj()).isEqualTo("12345678000195");
        }

        @Test
        @DisplayName("updateMe deve persistir openingHours")
        void updateMeShouldPersistOpeningHours() {
            java.util.List<OpeningHour> hours = java.util.List.of(
                    OpeningHour.builder()
                            .dayOfWeek(java.time.DayOfWeek.MONDAY)
                            .openTime("11:00")
                            .closeTime("23:00")
                            .closed(false)
                            .build());
            MerchantUpdateRequest req = MerchantUpdateRequest.builder()
                    .openingHours(hours)
                    .build();

            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
            given(merchantRepository.save(any(Merchant.class))).willAnswer(inv -> inv.getArgument(0));

            MerchantResponse result = merchantService.updateMe(merchantId, req);

            assertThat(result.getOpeningHours()).hasSize(1);
            assertThat(result.getOpeningHours().get(0).getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
        }
    }

    @Nested
    @DisplayName("getMyPreferences() / updateMyPreferences()")
    class Preferences {

        @Test
        @DisplayName("getMyPreferences deve retornar defaults quando merchant não tem prefs salvas")
        void getMyPreferencesShouldReturnDefaultsWhenNull() {
            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

            MerchantPreferences result = merchantService.getMyPreferences(merchantId);

            assertThat(result).isNotNull();
            assertThat(result.isRealtimeMarginCalc()).isTrue();
            assertThat(result.isMarginAlertBelow50Pct()).isFalse();
            // null = lojista ainda não definiu margem ideal para o pedido inteiro
            assertThat(result.getTargetOrderMarginPct()).isNull();
        }

        @Test
        @DisplayName("getMyPreferences deve retornar a margem ideal do pedido quando definida")
        void getMyPreferencesShouldReturnTargetOrderMargin() {
            merchant.setPreferences(MerchantPreferences.builder()
                    .targetOrderMarginPct(new BigDecimal("32.50"))
                    .build());

            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

            MerchantPreferences result = merchantService.getMyPreferences(merchantId);

            assertThat(result.getTargetOrderMarginPct()).isEqualByComparingTo("32.50");
        }

        @Test
        @DisplayName("updateMyPreferences deve gravar a margem ideal do pedido")
        void updateMyPreferencesShouldPersistTargetOrderMargin() {
            MerchantPreferences toSave = MerchantPreferences.builder()
                    .targetOrderMarginPct(new BigDecimal("28.00"))
                    .build();

            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
            given(merchantRepository.save(any(Merchant.class))).willAnswer(inv -> inv.getArgument(0));

            MerchantPreferences result = merchantService.updateMyPreferences(merchantId, toSave);

            assertThat(result.getTargetOrderMarginPct()).isEqualByComparingTo("28.00");
        }

        @Test
        @DisplayName("updateMyPreferences deve aceitar limpar a margem ideal do pedido")
        void updateMyPreferencesShouldAllowClearingTargetOrderMargin() {
            merchant.setPreferences(MerchantPreferences.builder()
                    .targetOrderMarginPct(new BigDecimal("28.00"))
                    .build());
            MerchantPreferences toSave = MerchantPreferences.builder()
                    .targetOrderMarginPct(null)
                    .build();

            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
            given(merchantRepository.save(any(Merchant.class))).willAnswer(inv -> inv.getArgument(0));

            MerchantPreferences result = merchantService.updateMyPreferences(merchantId, toSave);

            assertThat(result.getTargetOrderMarginPct()).isNull();
        }

        @Test
        @DisplayName("getMyPreferences deve retornar prefs persistidas quando existem")
        void getMyPreferencesShouldReturnPersisted() {
            MerchantPreferences stored = MerchantPreferences.builder()
                    .realtimeMarginCalc(false)
                    .marginAlertBelow50Pct(true)
                    .build();
            merchant.setPreferences(stored);

            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

            MerchantPreferences result = merchantService.getMyPreferences(merchantId);

            assertThat(result.isRealtimeMarginCalc()).isFalse();
            assertThat(result.isMarginAlertBelow50Pct()).isTrue();
        }

        @Test
        @DisplayName("updateMyPreferences deve sobrescrever as prefs e retornar o estado salvo")
        void updateMyPreferencesShouldOverwrite() {
            MerchantPreferences toSave = MerchantPreferences.builder()
                    .realtimeMarginCalc(false)
                    .warnUnregisteredIngredients(false)
                    .build();

            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
            given(merchantRepository.save(any(Merchant.class))).willAnswer(inv -> inv.getArgument(0));

            MerchantPreferences result = merchantService.updateMyPreferences(merchantId, toSave);

            assertThat(result.isRealtimeMarginCalc()).isFalse();
            assertThat(result.isWarnUnregisteredIngredients()).isFalse();
            then(merchantRepository).should().save(argThat(m -> m.getPreferences() == toSave));
        }
    }
}
