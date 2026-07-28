package com.jetmenu.billing;

import java.time.LocalDate;

public class DuplicateRevenueReportException extends RuntimeException {

    public DuplicateRevenueReportException(LocalDate referenceMonth) {
        super("Já existe um relatório de faturamento para o mês " + referenceMonth);
    }
}
