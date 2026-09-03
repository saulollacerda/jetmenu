package com.jetmenu.integration.anotaai.services;

import com.jetmenu.customer.Customer;
import com.jetmenu.customer.CustomerRepository;
import com.jetmenu.integration.anotaai.AnotaAIOrderDetailResponse;
import com.jetmenu.merchant.MerchantRepository;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolve um {@link Customer} a partir do payload de cliente vindo do Anota.AI.
 *
 * <p>Estratégia: busca por telefone dentro do merchant; se não existir, cria um novo cliente
 * com nome e identificador externos. Para payloads nulos, cria um cliente anônimo.
 */
@Component
public class AnotaAICustomerResolver {

    private final CustomerRepository customerRepository;
    private final MerchantRepository merchantRepository;

    public AnotaAICustomerResolver(CustomerRepository customerRepository,
                                    MerchantRepository merchantRepository) {
        this.customerRepository = customerRepository;
        this.merchantRepository = merchantRepository;
    }

    public Customer resolve(AnotaAIOrderDetailResponse.AnotaAICustomer remoteCustomer, UUID merchantId) {
        if (remoteCustomer == null) return createAnonymous(merchantId);

        String phone = remoteCustomer.getPhone();
        if (phone != null && !phone.isBlank()) {
            Optional<Customer> existing = customerRepository.findByPhoneAndMerchantId(phone, merchantId);
            if (existing.isPresent()) return existing.get();
        }

        Customer customer = Customer.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .name(remoteCustomer.getName() != null ? remoteCustomer.getName() : "Cliente Anota.AI")
                .phone(phone)
                .externalId(remoteCustomer.getId())
                .build();
        return customerRepository.save(customer);
    }

    private Customer createAnonymous(UUID merchantId) {
        return customerRepository.save(Customer.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .name("Cliente Anota.AI")
                .build());
    }
}
