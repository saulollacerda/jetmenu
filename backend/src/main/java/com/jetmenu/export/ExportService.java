package com.jetmenu.export;

import com.jetmenu.dashboard.DashboardService;
import com.jetmenu.dashboard.IngredientConsumption;
import com.jetmenu.order.Order;
import com.jetmenu.order.OrderItem;
import com.jetmenu.order.OrderItemExtraIngredient;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final OrderRepository orderRepository;
    private final DashboardService dashboardService;

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transactional(readOnly = true)
    public byte[] generateDashboardExport(UUID merchantId, LocalDate startDate, LocalDate endDate) {

        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atTime(23, 59, 59);

        List<Order> orders = orderRepository.findAllForReportByMerchantAndPeriodAndStatus(
                merchantId, startDt, endDt, OrderStatus.PAID);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);

            buildResumoFinanceiro(workbook, orders, start, end, headerStyle, boldStyle);
            buildPedidos(workbook, orders, headerStyle);
            buildDesempenhoPorProduto(workbook, orders, headerStyle);
            buildDesempenhoPorIngrediente(workbook, merchantId, start, end, headerStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar planilha de exportação", e);
        }
    }

    // -------------------------------------------------------------------------
    // Aba 1 — Resumo Financeiro
    // -------------------------------------------------------------------------

    private void buildResumoFinanceiro(XSSFWorkbook wb, List<Order> orders,
                                       LocalDate start, LocalDate end,
                                       CellStyle headerStyle, CellStyle boldStyle) {
        Sheet sheet = wb.createSheet("Resumo Financeiro");
        sheet.setColumnWidth(0, 7000);
        sheet.setColumnWidth(1, 5000);

        Row periodRow = sheet.createRow(0);
        createCell(periodRow, 0, "Período:", boldStyle);
        createCell(periodRow, 1, start + " a " + end, null);

        BigDecimal totalSales = orders.stream()
                .map(Order::getTotalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long orderCount = orders.size();

        BigDecimal averageTicket = orderCount > 0
                ? totalSales.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal estimatedProfit = orders.stream()
                .map(Order::getEstimatedProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal estimatedCost = totalSales.subtract(estimatedProfit);

        // Taxa: snapshot do pedido (order.getFeeRate()), nunca fee.getFeeRate() ao vivo — ver
        // javadoc de Order.feeRate. NOTA (pré-existente, não corrigida aqui): multiplica por
        // totalValue em vez do subtotal (totalValue − deliveryFee − serviceFee) usado por
        // OrderCalculations.calculateEstimatedProfit; mantido como estava, só a origem da
        // taxa mudou.
        BigDecimal feeTotal = orders.stream()
                .filter(o -> o.getFee() != null)
                .map(o -> o.getTotalValue().multiply(o.getFeeRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal netRevenue = totalSales.subtract(feeTotal).setScale(2, RoundingMode.HALF_UP);

        writeKpiRow(sheet, boldStyle, 1, "Faturamento", totalSales.doubleValue());
        writeKpiRow(sheet, boldStyle, 2, "Quantidade de Pedidos", (double) orderCount);
        writeKpiRow(sheet, boldStyle, 3, "Ticket Médio", averageTicket.doubleValue());
        writeKpiRow(sheet, boldStyle, 4, "Lucro Estimado", estimatedProfit.doubleValue());
        writeKpiRow(sheet, boldStyle, 5, "Taxa Total", feeTotal.doubleValue());
        writeKpiRow(sheet, boldStyle, 6, "Receita Líquida (sem taxas)", netRevenue.doubleValue());
        writeKpiRow(sheet, boldStyle, 7, "Custo Estimado", estimatedCost.doubleValue());
    }

    private void writeKpiRow(Sheet sheet, CellStyle labelStyle, int rowIndex, String label, double value) {
        Row row = sheet.createRow(rowIndex);
        createCell(row, 0, label, labelStyle);
        Cell cell = row.createCell(1);
        cell.setCellValue(value);
    }

    // -------------------------------------------------------------------------
    // Aba 2 — Pedidos
    // -------------------------------------------------------------------------

    private void buildPedidos(XSSFWorkbook wb, List<Order> orders, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Pedidos");

        String[] headers = {
                "Data/Hora", "Cliente", "Método de Pagamento", "Taxa %",
                "Valor da Taxa (R$)", "Total Bruto (R$)", "Total Líquido (R$)", "Custo Total (R$)",
                "Lucro Estimado (R$)", "Status"
        };
        writeHeaderRow(sheet, headers, headerStyle);

        int rowIdx = 1;
        for (Order order : orders) {
            Row row = sheet.createRow(rowIdx++);

            createCell(row, 0, order.getDateTime().format(DATE_TIME_FMT), null);
            createCell(row, 1, order.getCustomer() != null ? order.getCustomer().getName() : "", null);

            String pmName = order.getFee() != null ? order.getFee().getName() : "";
            createCell(row, 2, pmName, null);

            // Snapshot do pedido (order.getFeeRate()), não fee.getFeeRate() ao vivo — ver
            // javadoc de Order.feeRate.
            double feeRate = order.getFee() != null
                    ? order.getFeeRate()
                    .setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            row.createCell(3).setCellValue(feeRate);

            double feeAmount = order.getFee() != null
                    ? order.getTotalValue().multiply(order.getFeeRate()).divide(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            row.createCell(4).setCellValue(feeAmount);

            row.createCell(5).setCellValue(order.getTotalValue().doubleValue());

            double netValue = order.getTotalValue().doubleValue() - feeAmount;
            row.createCell(6).setCellValue(BigDecimal.valueOf(netValue).setScale(2, RoundingMode.HALF_UP).doubleValue());

            row.createCell(7).setCellValue(order.getTotalValue().subtract(order.getEstimatedProfit()).subtract(BigDecimal.valueOf(feeAmount)).doubleValue());

            row.createCell(8).setCellValue(order.getEstimatedProfit().doubleValue());

            createCell(row, 9, translateStatus(order.getStatus()), null);
        }

        autoSizeColumns(sheet, headers.length);
    }

    // -------------------------------------------------------------------------
    // Aba 3 — Desempenho por Produto
    // -------------------------------------------------------------------------

    private void buildDesempenhoPorProduto(XSSFWorkbook wb, List<Order> orders, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Desempenho por Produto");

        String[] headers = {
                "Produto", "Categoria", "Qtd Vendida", "Receita Total (R$)",
                "Custo Total (R$)", "Lucro Total (R$)", "Margem (%)"
        };
        writeHeaderRow(sheet, headers, headerStyle);

        record ProductKey(String name, String category) {}

        record ProductAgg(double qtd, double revenue, double cost) {}

        Map<ProductKey, ProductAgg> agg = new LinkedHashMap<>();

        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                String name = item.getProduct() != null ? item.getProduct().getName() : "";
                String cat = (item.getProduct() != null && item.getProduct().getCategory() != null)
                        ? item.getProduct().getCategory().getName() : "";

                ProductKey key = new ProductKey(name, cat);
                ProductAgg prev = agg.getOrDefault(key, new ProductAgg(0, 0, 0));
                double itemRevenue = item.getUnitPrice().doubleValue() * item.getQuantity();
                double itemCost = calculateItemCost(item).doubleValue();
                agg.put(key, new ProductAgg(
                        prev.qtd() + item.getQuantity(),
                        prev.revenue() + itemRevenue,
                        prev.cost() + itemCost
                ));
            }
        }

        int rowIdx = 1;
        for (Map.Entry<ProductKey, ProductAgg> entry : agg.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            ProductKey key = entry.getKey();
            ProductAgg val = entry.getValue();

            createCell(row, 0, key.name(), null);
            createCell(row, 1, key.category(), null);
            row.createCell(2).setCellValue(val.qtd());
            row.createCell(3).setCellValue(BigDecimal.valueOf(val.revenue()).setScale(2, RoundingMode.HALF_UP).doubleValue());
            row.createCell(4).setCellValue(BigDecimal.valueOf(val.cost()).setScale(2, RoundingMode.HALF_UP).doubleValue());

            double profit = val.revenue() - val.cost();
            row.createCell(5).setCellValue(BigDecimal.valueOf(profit).setScale(2, RoundingMode.HALF_UP).doubleValue());

            double margin = val.revenue() > 0
                    ? BigDecimal.valueOf(profit / val.revenue() * 100)
                    .setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            row.createCell(6).setCellValue(margin);
        }

        autoSizeColumns(sheet, headers.length);
    }

    // -------------------------------------------------------------------------
    // Aba 4 — Desempenho por Ingrediente
    // -------------------------------------------------------------------------

    private void buildDesempenhoPorIngrediente(XSSFWorkbook wb, UUID merchantId,
                                               LocalDate start, LocalDate end,
                                               CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Desempenho por Ingrediente");

        String[] headers = {"Ingrediente", "Unidade", "Quantidade Total", "Custo Total (R$)"};
        writeHeaderRow(sheet, headers, headerStyle);

        List<IngredientConsumption> ranking = dashboardService.ingredientRanking(merchantId, start, end);

        int rowIdx = 1;
        for (IngredientConsumption ingredient : ranking) {
            Row row = sheet.createRow(rowIdx++);
            createCell(row, 0, ingredient.getIngredientName(), null);
            createCell(row, 1, ingredient.getUnit(), null);
            row.createCell(2).setCellValue(ingredient.getTotalQuantity().doubleValue());
            row.createCell(3).setCellValue(
                    ingredient.getTotalCost().setScale(2, RoundingMode.HALF_UP).doubleValue());
        }

        autoSizeColumns(sheet, headers.length);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void writeHeaderRow(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            createCell(row, i, headers[i], style);
        }
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    private void autoSizeColumns(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createBoldStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private BigDecimal calculateItemCost(OrderItem item) {
        BigDecimal baseUnitCost = item.getUnitCost() != null
                ? item.getUnitCost()
                : BigDecimal.ZERO;
        BigDecimal extrasUnitCost = calculateExtrasUnitCost(item);
        return baseUnitCost.add(extrasUnitCost).multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private BigDecimal calculateExtrasUnitCost(OrderItem item) {
        if (item.getExtraIngredients() == null || item.getExtraIngredients().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return item.getExtraIngredients().stream()
                .map(extra -> extra.getQuantity().multiply(extra.getCostPerUnit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String translateStatus(OrderStatus status) {
        return switch (status) {
            case PAID -> "Pago";
            case PENDING -> "Pendente";
            case READY -> "Pronto";
            case DELIVERED -> "Entregue";
            case CANCELLED -> "Cancelado";
            case TEST -> "Teste";
        };
    }
}
