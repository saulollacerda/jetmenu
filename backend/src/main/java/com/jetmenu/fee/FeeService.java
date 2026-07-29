package com.jetmenu.fee;

import com.jetmenu.merchant.MerchantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FeeService {

    private final FeeRepository feeRepository;
    private final MerchantRepository merchantRepository;

    public FeeService(FeeRepository feeRepository,
                      MerchantRepository merchantRepository) {
        this.feeRepository = feeRepository;
        this.merchantRepository = merchantRepository;
    }

    public FeeResponse create(UUID merchantId, FeeRequest request) {
        if (feeRepository.existsByNameAndMerchantId(request.getName(), merchantId)) {
            throw new DuplicateFeeException("nome");
        }

        Fee fee = Fee.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .name(request.getName())
                .feeRate(request.getFeeRate())
                .build();

        Fee saved = feeRepository.save(fee);
        return toResponse(saved);
    }

    public FeeResponse findById(UUID merchantId, UUID id) {
        Fee fee = feeRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new FeeNotFoundException(id));
        return toResponse(fee);
    }

    public Page<FeeResponse> findAll(UUID merchantId, String search, Pageable pageable) {
        String term = search == null ? "" : search;
        return feeRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, term, pageable)
                .map(this::toResponse);
    }

    public FeeResponse update(UUID merchantId, UUID id, FeeRequest request) {
        Fee fee = feeRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new FeeNotFoundException(id));

        fee.setName(request.getName());
        fee.setFeeRate(request.getFeeRate());

        Fee saved = feeRepository.save(fee);
        return toResponse(saved);
    }

    public void delete(UUID merchantId, UUID id) {
        if (!feeRepository.existsByIdAndMerchantId(id, merchantId)) {
            throw new FeeNotFoundException(id);
        }
        feeRepository.deleteByIdAndMerchantId(id, merchantId);
    }

    private FeeResponse toResponse(Fee fee) {
        return FeeResponse.builder()
                .id(fee.getId())
                .name(fee.getName())
                .feeRate(fee.getFeeRate())
                .build();
    }
}
