package com.jetmenu.fee;

import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeeService")
class FeeServiceTest {

    @Mock
    private FeeRepository feeRepository;

    @Mock
    private MerchantRepository merchantRepository;


    @InjectMocks
    private FeeService feeService;

    private UUID merchantId;
    private UUID feeId;
    private Fee fee;
    private FeeRequest request;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        lenient().when(merchantRepository.getReferenceById(any())).thenReturn(Merchant.builder().id(merchantId).build());
        feeId = UUID.randomUUID();

        request = FeeRequest.builder()
                .name("Crédito")
                .feeRate(new BigDecimal("2.5000"))
                .build();

        fee = Fee.builder()
                .id(feeId)
                .merchant(Merchant.builder().id(merchantId).build())
                .name("Crédito")
                .feeRate(new BigDecimal("2.5000"))
                .build();
    }

    // -------------------------------------------------------------------------
    // create()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("deve criar taxa com dados válidos e retornar FeeResponse")
        void shouldCreateAndReturnResponse() {
            given(feeRepository.existsByNameAndMerchantId(request.getName(), merchantId)).willReturn(false);
            given(feeRepository.save(any(Fee.class))).willReturn(fee);

            FeeResponse result = feeService.create(merchantId, request);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(feeId);
            assertThat(result.getName()).isEqualTo("Crédito");
            assertThat(result.getFeeRate()).isEqualByComparingTo(new BigDecimal("2.5000"));
            then(feeRepository).should().save(argThat(f -> merchantId.equals(f.getMerchant().getId())));
        }

        @Test
        @DisplayName("deve lançar DuplicateFeeException quando nome já está cadastrado")
        void shouldThrowWhenNameAlreadyExists() {
            given(feeRepository.existsByNameAndMerchantId(request.getName(), merchantId)).willReturn(true);

            assertThatThrownBy(() -> feeService.create(merchantId, request))
                    .isInstanceOf(DuplicateFeeException.class);

            then(feeRepository).should(never()).save(any(Fee.class));
        }

        @Test
        @DisplayName("deve permitir criar taxa com valor zero (ex: Dinheiro)")
        void shouldAllowZeroFeeRate() {
            FeeRequest cashRequest = FeeRequest.builder()
                    .name("Dinheiro")
                    .feeRate(BigDecimal.ZERO)
                    .build();
            Fee cash = Fee.builder()
                    .id(feeId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("Dinheiro")
                    .feeRate(BigDecimal.ZERO)
                    .build();

            given(feeRepository.existsByNameAndMerchantId("Dinheiro", merchantId)).willReturn(false);
            given(feeRepository.save(any(Fee.class))).willReturn(cash);

            FeeResponse result = feeService.create(merchantId, cashRequest);

            assertThat(result.getFeeRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // -------------------------------------------------------------------------
    // findById()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("deve retornar FeeResponse quando taxa existe")
        void shouldReturnResponseWhenExists() {
            given(feeRepository.findByIdAndMerchantId(feeId, merchantId))
                    .willReturn(Optional.of(fee));

            FeeResponse result = feeService.findById(merchantId, feeId);

            assertThat(result.getId()).isEqualTo(feeId);
            assertThat(result.getName()).isEqualTo("Crédito");
        }

        @Test
        @DisplayName("deve lançar FeeNotFoundException quando não encontrada")
        void shouldThrowWhenNotFound() {
            given(feeRepository.findByIdAndMerchantId(feeId, merchantId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> feeService.findById(merchantId, feeId))
                    .isInstanceOf(FeeNotFoundException.class);
        }
    }

    // -------------------------------------------------------------------------
    // findAll()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findAll(search, pageable)")
    class FindAll {

        @Test
        @DisplayName("deve retornar página de taxas filtrada por nome (contains, case-insensitive)")
        void shouldReturnPagedFilteredByName() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(feeRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, "cred", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(fee), pageable, 1));

            org.springframework.data.domain.Page<FeeResponse> result =
                    feeService.findAll(merchantId, "cred", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Crédito");
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("deve tratar search nulo como string vazia")
        void shouldTreatNullSearchAsEmpty() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(feeRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, "", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0));

            org.springframework.data.domain.Page<FeeResponse> result =
                    feeService.findAll(merchantId, null, pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // update()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("deve atualizar taxa e retornar resposta atualizada")
        void shouldUpdateAndReturnUpdatedResponse() {
            FeeRequest updateRequest = FeeRequest.builder()
                    .name("Débito")
                    .feeRate(new BigDecimal("1.0000"))
                    .build();

            Fee updated = Fee.builder()
                    .id(feeId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("Débito")
                    .feeRate(new BigDecimal("1.0000"))
                    .build();

            given(feeRepository.findByIdAndMerchantId(feeId, merchantId))
                    .willReturn(Optional.of(fee));
            given(feeRepository.save(any(Fee.class))).willReturn(updated);

            FeeResponse result = feeService.update(merchantId, feeId, updateRequest);

            assertThat(result.getName()).isEqualTo("Débito");
            assertThat(result.getFeeRate()).isEqualByComparingTo(new BigDecimal("1.0000"));
        }

        @Test
        @DisplayName("deve lançar FeeNotFoundException ao atualizar taxa inexistente")
        void shouldThrowWhenNotFoundForUpdate() {
            given(feeRepository.findByIdAndMerchantId(feeId, merchantId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> feeService.update(merchantId, feeId, request))
                    .isInstanceOf(FeeNotFoundException.class);

            then(feeRepository).should(never()).save(any(Fee.class));
        }
    }

    // -------------------------------------------------------------------------
    // delete()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("deve deletar taxa existente sem lançar exceção")
        void shouldDeleteExisting() {
            given(feeRepository.existsByIdAndMerchantId(feeId, merchantId)).willReturn(true);
            willDoNothing().given(feeRepository).deleteByIdAndMerchantId(feeId, merchantId);

            assertThatNoException().isThrownBy(() -> feeService.delete(merchantId, feeId));

            then(feeRepository).should().deleteByIdAndMerchantId(feeId, merchantId);
        }

        @Test
        @DisplayName("deve lançar FeeNotFoundException ao deletar taxa inexistente")
        void shouldThrowWhenNotFoundForDelete() {
            given(feeRepository.existsByIdAndMerchantId(feeId, merchantId)).willReturn(false);

            assertThatThrownBy(() -> feeService.delete(merchantId, feeId))
                    .isInstanceOf(FeeNotFoundException.class);

            then(feeRepository).should(never()).deleteByIdAndMerchantId(any(), any());
        }
    }
}
