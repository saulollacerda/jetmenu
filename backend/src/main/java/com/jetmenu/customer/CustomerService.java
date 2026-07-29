package com.jetmenu.customer;

import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final MerchantRepository merchantRepository;
    private final OrderRepository orderRepository;

    public CustomerService(CustomerRepository customerRepository,
                           MerchantRepository merchantRepository,
                           OrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.merchantRepository = merchantRepository;
        this.orderRepository = orderRepository;
    }

    public CustomerResponse create(UUID merchantId, CustomerRequest request) {
        Customer customer = Customer.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .neighborhood(request.getNeighborhood())
                .notes(request.getNotes())
                .build();

        Customer saved = customerRepository.save(customer);
        return toResponse(saved, Aggregates.EMPTY);
    }

    public CustomerResponse findById(UUID merchantId, UUID id) {
        Customer customer = customerRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        Aggregates agg = fetchAggregatesForOne(customer.getId(), merchantId);
        return toResponse(customer, agg);
    }

    public Page<CustomerResponse> findAll(UUID merchantId, String search, Pageable pageable) {
        String term = search == null ? "" : search;
        Page<Customer> page = customerRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, term, pageable);
        Map<UUID, Aggregates> aggs = fetchAggregatesForPage(page.getContent(), merchantId);
        return page.map(c -> toResponse(c, aggs.getOrDefault(c.getId(), Aggregates.EMPTY)));
    }

    public CustomerResponse update(UUID merchantId, UUID id, CustomerRequest request) {
        Customer customer = customerRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        customer.setName(request.getName());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        if (request.getNeighborhood() != null) {
            customer.setNeighborhood(request.getNeighborhood());
        }
        if (request.getNotes() != null) {
            customer.setNotes(request.getNotes());
        }

        Customer saved = customerRepository.save(customer);
        Aggregates agg = fetchAggregatesForOne(saved.getId(), merchantId);
        return toResponse(saved, agg);
    }

    @Transactional
    public void delete(UUID merchantId, UUID id) {
        if (!customerRepository.existsByIdAndMerchantId(id, merchantId)) {
            throw new CustomerNotFoundException(id);
        }
        customerRepository.deleteByIdAndMerchantId(id, merchantId);
    }

    private CustomerResponse toResponse(Customer customer, Aggregates agg) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .neighborhood(customer.getNeighborhood())
                .notes(customer.getNotes())
                .orderCount(agg.orderCount)
                .lifetimeValue(agg.lifetimeValue)
                .lastOrderAt(agg.lastOrderAt)
                .preferredOrigin(agg.preferredOrigin)
                .build();
    }

    private Aggregates fetchAggregatesForOne(UUID customerId, UUID merchantId) {
        Map<UUID, Aggregates> map = fetchAggregatesForPage(
                java.util.Collections.singletonList(
                        Customer.builder().id(customerId).build()),
                merchantId);
        return map.getOrDefault(customerId, Aggregates.EMPTY);
    }

    private Map<UUID, Aggregates> fetchAggregatesForPage(List<Customer> customers, UUID merchantId) {
        if (customers.isEmpty()) return Map.of();
        List<UUID> ids = customers.stream().map(Customer::getId).toList();

        Map<UUID, Aggregates> result = new HashMap<>();
        for (Object[] row : orderRepository.aggregatesByCustomerForMerchant(merchantId, ids)) {
            UUID cid = (UUID) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal lifetime = (BigDecimal) row[2];
            LocalDateTime last = (LocalDateTime) row[3];
            result.put(cid, new Aggregates(count, lifetime, last, null));
        }

        // preferredOrigin: pega a origin com maior count por customer
        Map<UUID, Map<OrderOrigin, Long>> originCounts = new HashMap<>();
        for (Object[] row : orderRepository.originBreakdownByCustomerForMerchant(merchantId, ids)) {
            UUID cid = (UUID) row[0];
            OrderOrigin origin = (OrderOrigin) row[1];
            long count = ((Number) row[2]).longValue();
            originCounts.computeIfAbsent(cid, k -> new HashMap<>()).put(origin, count);
        }
        for (Map.Entry<UUID, Map<OrderOrigin, Long>> entry : originCounts.entrySet()) {
            OrderOrigin pref = entry.getValue().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            Aggregates existing = result.getOrDefault(entry.getKey(), Aggregates.EMPTY);
            result.put(entry.getKey(), new Aggregates(
                    existing.orderCount, existing.lifetimeValue, existing.lastOrderAt, pref));
        }
        return result;
    }

    private record Aggregates(long orderCount, BigDecimal lifetimeValue,
                              LocalDateTime lastOrderAt, OrderOrigin preferredOrigin) {
        static final Aggregates EMPTY = new Aggregates(0L, BigDecimal.ZERO, null, null);
    }
}
