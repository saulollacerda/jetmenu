package com.jetmenu.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RevenueReportRepository extends JpaRepository<RevenueReport, UUID> {

    Optional<RevenueReport> findByMerchantIdAndReferenceMonth(UUID merchantId, LocalDate referenceMonth);

    List<RevenueReport> findByMerchantIdOrderByReferenceMonthDesc(UUID merchantId);
}
