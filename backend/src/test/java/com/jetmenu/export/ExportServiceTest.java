package com.jetmenu.export;

import com.jetmenu.merchant.Merchant;

import com.jetmenu.category.Category;
import com.jetmenu.customer.Customer;
import com.jetmenu.dashboard.DashboardService;
import com.jetmenu.dashboard.IngredientConsumption;
import com.jetmenu.order.Order;
import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.order.OrderItem;
import com.jetmenu.order.OrderItemExtraIngredient;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.order.OrderStatus;
import com.jetmenu.fee.Fee;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductStatus;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportService")
class ExportServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private ExportService exportService;

    private UUID merchantId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Customer customer;
    private Fee fee;
    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        startDate = LocalDate.of(2026, 3, 1);
        endDate = LocalDate.of(2026, 3, 31);

        customer = Customer.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .name("João Silva")
                .build();

        fee = Fee.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .name("Cartão de Crédito")
                .feeRate(new BigDecimal("2.99"))
                .build();

        category = Category.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .name("Lanches")
                .build();

        product = Product.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .name("X-Burguer")
                .price(new BigDecimal("25.00"))
                .status(ProductStatus.ACTIVE)
                .category(category)
                .build();
    }

    private Order buildOrder(LocalDateTime dateTime, BigDecimal totalValue,
                             BigDecimal estimatedProfit, Fee f,
                             List<OrderItem> items) {
        return Order.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .dateTime(dateTime)
                .customer(customer)
                .fee(f)
                .status(OrderStatus.PAID)
                .totalValue(totalValue)
                .estimatedProfit(estimatedProfit)
                .items(items)
                .build();
    }

    private OrderItem buildItem(Product p, int quantity, BigDecimal unitPrice) {
        return OrderItem.builder()
                .id(UUID.randomUUID())
                .product(p)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .build();
    }

    // -------------------------------------------------------------------------
    // generateDashboardExport() — byte array
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("generateDashboardExport() — byte array")
    class GenerateByteArray {

        @Test
        @DisplayName("deve retornar byte array não-vazio para um workbook válido")
        void shouldReturnNonEmptyByteArray() throws Exception {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("deve retornar um arquivo .xlsx válido (legível pelo Apache POI)")
        void shouldReturnValidXlsxFile() throws Exception {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            assertThatCode(() -> {
                try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                    assertThat(wb.getNumberOfSheets()).isGreaterThan(0);
                }
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("deve gerar workbook com 4 abas")
        void shouldGenerateWorkbookWithFourSheets() throws Exception {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                assertThat(wb.getNumberOfSheets()).isEqualTo(4);
            }
        }
    }

    // -------------------------------------------------------------------------
    // generateDashboardExport() — Aba Resumo Financeiro
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("generateDashboardExport() — Resumo Financeiro")
    class ResumoFinanceiro {

        @Test
        @DisplayName("deve criar aba 'Resumo Financeiro' como primeira aba")
        void shouldCreateResumoFinanceiroAsFirstSheet() throws Exception {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(any(), any(), any(), any()))
                    .willReturn(List.of());

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Resumo Financeiro");
            }
        }

        @Test
        @DisplayName("deve calcular totalSales corretamente no Resumo Financeiro")
        void shouldCalculateTotalSalesInSummary() throws Exception {
            OrderItem item = buildItem(product, 2, new BigDecimal("25.00"));
            List<Order> orders = List.of(
                    buildOrder(startDate.atTime(10, 0), new BigDecimal("100.00"),
                            new BigDecimal("30.00"), fee, List.of(item)),
                    buildOrder(startDate.atTime(14, 0), new BigDecimal("200.00"),
                            new BigDecimal("60.00"), null, List.of())
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(orders);

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                var sheet = wb.getSheet("Resumo Financeiro");
                // Row 0 = cabeçalho com período, Row 1 = Total de Vendas label+valor
                double totalSales = sheet.getRow(1).getCell(1).getNumericCellValue();
                assertThat(totalSales).isEqualTo(300.00);
            }
        }

        @Test
        @DisplayName("deve calcular taxaTotal apenas para pedidos com taxa")
        void shouldCalculateFeeOnlyForOrdersWithFee() throws Exception {
            OrderItem item = buildItem(product, 1, new BigDecimal("100.00"));
            List<Order> orders = List.of(
                    buildOrder(startDate.atTime(10, 0), new BigDecimal("100.00"),
                            new BigDecimal("30.00"), fee, List.of(item)),
                    buildOrder(startDate.atTime(14, 0), new BigDecimal("200.00"),
                            new BigDecimal("60.00"), null, List.of())
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(orders);

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                var sheet = wb.getSheet("Resumo Financeiro");
                // Row 5 = Taxa Total
                double feeTotal = sheet.getRow(5).getCell(1).getNumericCellValue();
                // 100 * 0.0299 = 2.99
                assertThat(feeTotal).isCloseTo(2.99, within(0.01));
            }
        }
    }

    // -------------------------------------------------------------------------
    // generateDashboardExport() — Aba Pedidos
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("generateDashboardExport() — Pedidos")
    class Pedidos {

        @Test
        @DisplayName("deve criar aba 'Pedidos' como segunda aba")
        void shouldCreatePedidosAsSecondSheet() throws Exception {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(any(), any(), any(), any()))
                    .willReturn(List.of());

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                assertThat(wb.getSheetAt(1).getSheetName()).isEqualTo("Pedidos");
            }
        }

        @Test
        @DisplayName("deve ter linha de cabeçalho e uma linha por pedido")
        void shouldHaveHeaderAndOneRowPerOrder() throws Exception {
            OrderItem item = buildItem(product, 1, new BigDecimal("25.00"));
            List<Order> orders = List.of(
                    buildOrder(startDate.atTime(10, 0), new BigDecimal("100.00"),
                            new BigDecimal("30.00"), fee, List.of(item)),
                    buildOrder(startDate.atTime(14, 0), new BigDecimal("200.00"),
                            new BigDecimal("60.00"), null, List.of())
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(orders);

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                var sheet = wb.getSheet("Pedidos");
                // Row 0 = cabeçalho, rows 1..N = dados
                assertThat(sheet.getLastRowNum()).isEqualTo(2);
            }
        }

        @Test
        @DisplayName("deve preencher nome do cliente na coluna correta")
        void shouldFillCustomerName() throws Exception {
            List<Order> orders = List.of(
                    buildOrder(startDate.atTime(10, 0), new BigDecimal("100.00"),
                            new BigDecimal("30.00"), fee, List.of())
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(orders);

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                var sheet = wb.getSheet("Pedidos");
                // coluna 1 = cliente
                String customerName = sheet.getRow(1).getCell(1).getStringCellValue();
                assertThat(customerName).isEqualTo("João Silva");
            }
        }
    }

    // -------------------------------------------------------------------------
    // generateDashboardExport() — Aba Desempenho por Produto
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("generateDashboardExport() — Desempenho por Produto")
    class DesempenhoPorProduto {

        @Test
        @DisplayName("deve criar aba 'Desempenho por Produto' como terceira aba")
        void shouldCreateDesempenhoPorProdutoAsThirdSheet() throws Exception {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(any(), any(), any(), any()))
                    .willReturn(List.of());

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                assertThat(wb.getSheetAt(2).getSheetName()).isEqualTo("Desempenho por Produto");
            }
        }

        @Test
        @DisplayName("deve agregar itens do mesmo produto em uma única linha")
        void shouldAggregateItemsByProduct() throws Exception {
            OrderItem item1 = buildItem(product, 3, new BigDecimal("25.00"));
            OrderItem item2 = buildItem(product, 2, new BigDecimal("25.00"));
            List<Order> orders = List.of(
                    buildOrder(startDate.atTime(10, 0), new BigDecimal("75.00"),
                            new BigDecimal("30.00"), null, List.of(item1)),
                    buildOrder(startDate.atTime(14, 0), new BigDecimal("50.00"),
                            new BigDecimal("20.00"), null, List.of(item2))
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(orders);

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                var sheet = wb.getSheet("Desempenho por Produto");
                // cabeçalho + 1 linha de produto (agrupado)
                assertThat(sheet.getLastRowNum()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("deve calcular receita total e quantidade corretamente no agrupamento")
        void shouldCalculateTotalsCorrectly() throws Exception {
            OrderItem item1 = buildItem(product, 3, new BigDecimal("25.00"));
            OrderItem item2 = buildItem(product, 2, new BigDecimal("25.00"));
            List<Order> orders = List.of(
                    buildOrder(startDate.atTime(10, 0), new BigDecimal("75.00"),
                            new BigDecimal("30.00"), null, List.of(item1)),
                    buildOrder(startDate.atTime(14, 0), new BigDecimal("50.00"),
                            new BigDecimal("20.00"), null, List.of(item2))
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(orders);

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                var sheet = wb.getSheet("Desempenho por Produto");
                // col 0 = produto, col 2 = qtd total, col 3 = receita total
                double qtdTotal = sheet.getRow(1).getCell(2).getNumericCellValue();
                double receitaTotal = sheet.getRow(1).getCell(3).getNumericCellValue();
                assertThat(qtdTotal).isEqualTo(5.0); // 3 + 2
                assertThat(receitaTotal).isEqualTo(125.00); // 5 * 25
            }
        }

        @Test
        @DisplayName("deve calcular custo, lucro e margem usando custo base + extras (per product unit)")
        void shouldCalculateCostProfitAndMarginIncludingExtras() throws Exception {
            // product.estimatedCost = 10.00, unitPrice = 25.00, qty = 2
            // extra: quantity = 1 unit per product, costPerUnit = 5.00
            // baseCost per order = (10 + 1*5) * 2 = 30
            // revenue = 25 * 2 = 50
            // profit = 50 - 30 = 20
            // margin = 20/50 * 100 = 40
            OrderItemExtraIngredient extra = OrderItemExtraIngredient.builder()
                    .id(UUID.randomUUID())
                    .ingredient(Ingredient.builder().id(UUID.randomUUID()).build())
                    .quantity(new BigDecimal("1.000000"))
                    .costPerUnit(new BigDecimal("5.0000"))
                    .ingredientName("Bacon")
                    .ingredientUnit("un")
                    .build();

            OrderItem item = OrderItem.builder()
                    .id(UUID.randomUUID())
                    .product(product)
                    .quantity(2)
                    .unitPrice(new BigDecimal("25.00"))
                    .unitCost(new BigDecimal("10.00"))
                    .extraIngredients(new ArrayList<>(List.of(extra)))
                    .build();

            List<Order> orders = List.of(
                    buildOrder(startDate.atTime(10, 0), new BigDecimal("50.00"),
                            new BigDecimal("20.00"), null, List.of(item))
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(orders);

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                var sheet = wb.getSheet("Desempenho por Produto");
                // col 4 = custo total, col 5 = lucro total, col 6 = margem %
                double custoTotal = sheet.getRow(1).getCell(4).getNumericCellValue();
                double lucroTotal = sheet.getRow(1).getCell(5).getNumericCellValue();
                double margem = sheet.getRow(1).getCell(6).getNumericCellValue();
                assertThat(custoTotal).isEqualTo(30.00);
                assertThat(lucroTotal).isEqualTo(20.00);
                assertThat(margem).isEqualTo(40.00);
            }
        }
    }

    // -------------------------------------------------------------------------
    // generateDashboardExport() — Aba Desempenho por Ingrediente
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("generateDashboardExport() — Desempenho por Ingrediente")
    class DesempenhoPorIngrediente {

        @Test
        @DisplayName("deve criar aba 'Desempenho por Ingrediente' como quarta aba, após 'Desempenho por Produto'")
        void shouldCreateDesempenhoPorIngredienteAsFourthSheet() throws Exception {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(any(), any(), any(), any()))
                    .willReturn(List.of());

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                assertThat(wb.getSheetAt(2).getSheetName()).isEqualTo("Desempenho por Produto");
                assertThat(wb.getSheetAt(3).getSheetName()).isEqualTo("Desempenho por Ingrediente");
            }
        }

        @Test
        @DisplayName("deve preencher uma linha por ingrediente vindo do ranking do serviço")
        void shouldWriteOneRowPerIngredientFromService() throws Exception {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(any(), any(), any(), any()))
                    .willReturn(List.of());
            given(dashboardService.ingredientRanking(eq(merchantId), any(), any()))
                    .willReturn(List.of(
                            IngredientConsumption.builder()
                                    .ingredientName("Queijo")
                                    .unit("g")
                                    .totalQuantity(new BigDecimal("100"))
                                    .totalCost(new BigDecimal("12.00"))
                                    .build(),
                            IngredientConsumption.builder()
                                    .ingredientName("Bacon")
                                    .unit("g")
                                    .totalQuantity(new BigDecimal("250"))
                                    .totalCost(new BigDecimal("10.00"))
                                    .build()
                    ));

            byte[] result = exportService.generateDashboardExport(merchantId, startDate, endDate);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
                var sheet = wb.getSheet("Desempenho por Ingrediente");
                // header + 2 ingredient rows
                assertThat(sheet.getLastRowNum()).isEqualTo(2);

                // col 0 = Ingrediente, 1 = Unidade, 2 = Quantidade Total, 3 = Custo Total
                assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Queijo");
                assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("g");
                assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(100.0);
                assertThat(sheet.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(12.00);

                assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Bacon");
                assertThat(sheet.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(250.0);
                assertThat(sheet.getRow(2).getCell(3).getNumericCellValue()).isEqualTo(10.00);
            }
        }
    }
}
