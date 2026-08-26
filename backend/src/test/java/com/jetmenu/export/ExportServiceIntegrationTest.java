package com.jetmenu.export;

import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.customer.Customer;
import com.jetmenu.customer.CustomerRepository;
import com.jetmenu.fee.Fee;
import com.jetmenu.fee.FeeRepository;
import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.order.Order;
import com.jetmenu.order.OrderItem;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.order.OrderStatus;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.ProductStatus;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExportService — integração com Postgres")
class ExportServiceIntegrationTest extends IntegrationTestBase {

    @Autowired private ExportService exportService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private FeeRepository feeRepository;

    private Merchant merchant;

    @BeforeEach
    void setup() {
        merchant = createMerchant();
    }

    @Test
    @DisplayName("generateDashboardExport deve retornar planilha XLSX válida com 4 abas")
    void generateDashboardExport_shouldReturnValidXlsxWithFourSheets() throws Exception {
        seedOrder("João", "Açaí 500ml", new BigDecimal("50.00"));

        byte[] bytes = exportService.generateDashboardExport(merchant.getId(), LocalDate.now(), LocalDate.now());

        assertThat(bytes).isNotEmpty();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(4);
            assertThat(wb.getSheet("Resumo Financeiro")).isNotNull();
            assertThat(wb.getSheet("Pedidos")).isNotNull();
            assertThat(wb.getSheet("Desempenho por Produto")).isNotNull();
            assertThat(wb.getSheet("Desempenho por Ingrediente")).isNotNull();
        }
    }

    @Test
    @DisplayName("Aba Resumo Financeiro deve mostrar totalSales correto")
    void generateDashboardExport_resumoShouldShowCorrectTotalSales() throws Exception {
        seedOrder("A", "Produto A", new BigDecimal("30.00"));
        seedOrder("B", "Produto B", new BigDecimal("70.00"));

        byte[] bytes = exportService.generateDashboardExport(merchant.getId(), LocalDate.now(), LocalDate.now());

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet resumo = wb.getSheet("Resumo Financeiro");
            // linha 1 = Faturamento
            double faturamento = resumo.getRow(1).getCell(1).getNumericCellValue();
            assertThat(faturamento).isEqualTo(100.0);
            // linha 2 = Quantidade de Pedidos
            double qtd = resumo.getRow(2).getCell(1).getNumericCellValue();
            assertThat(qtd).isEqualTo(2.0);
        }
    }

    @Test
    @DisplayName("Aba Pedidos deve listar um row por pedido")
    void generateDashboardExport_pedidosShouldHaveRowPerOrder() throws Exception {
        seedOrder("A", "Produto", new BigDecimal("10.00"));
        seedOrder("B", "Produto", new BigDecimal("20.00"));

        byte[] bytes = exportService.generateDashboardExport(merchant.getId(), LocalDate.now(), LocalDate.now());

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet pedidos = wb.getSheet("Pedidos");
            // header + 2 rows
            assertThat(pedidos.getLastRowNum()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("Aba Desempenho por Produto deve agrupar por produto")
    void generateDashboardExport_desempenhoShouldGroupByProduct() throws Exception {
        seedOrder("A", "Açaí 330ml", new BigDecimal("15.00"));
        seedOrder("B", "Açaí 330ml", new BigDecimal("15.00"));
        seedOrder("C", "Açaí 500ml", new BigDecimal("20.00"));

        byte[] bytes = exportService.generateDashboardExport(merchant.getId(), LocalDate.now(), LocalDate.now());

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet desemp = wb.getSheet("Desempenho por Produto");
            // header + 2 produtos únicos
            assertThat(desemp.getLastRowNum()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("export deve incluir taxa quando o pedido tem Fee associado")
    void generateDashboardExport_shouldIncludeFeeInfo() throws Exception {
        Fee fee = feeRepository.save(Fee.builder()
                .merchant(merchant).name("Pix").feeRate(new BigDecimal("0.99")).build());
        Order o = newOrder("C", "P", new BigDecimal("100.00"));
        o.setFee(fee);
        // Snapshot da taxa vigente na venda — ExportService lê o.getFeeRate(), não
        // fee.getFeeRate() ao vivo.
        o.setFeeRate(fee.getFeeRate());
        orderRepository.save(o);

        byte[] bytes = exportService.generateDashboardExport(merchant.getId(), LocalDate.now(), LocalDate.now());

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet pedidos = wb.getSheet("Pedidos");
            String paymentMethod = pedidos.getRow(1).getCell(2).getStringCellValue();
            assertThat(paymentMethod).isEqualTo("Pix");
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private void seedOrder(String customerName, String productName, BigDecimal total) {
        orderRepository.save(newOrder(customerName, productName, total));
    }

    private Order newOrder(String customerName, String productName, BigDecimal total) {
        Category cat = categoryRepository.save(Category.builder()
                .merchant(merchant).name("Açaí").build());
        Product product = productRepository.save(Product.builder()
                .merchant(merchant).name(productName).price(total)
                .status(ProductStatus.ACTIVE).category(cat).build());
        Customer customer = customerRepository.save(Customer.builder()
                .merchant(merchant).name(customerName).phone(String.valueOf(System.nanoTime())).build());

        Order order = Order.builder()
                .merchant(merchant).customer(customer)
                .dateTime(LocalDate.now().atTime(12, 0))
                .status(OrderStatus.PAID)
                .totalValue(total)
                .estimatedProfit(total.multiply(new BigDecimal("0.5")))
                .origin(OrderOrigin.JETMENU).build();
        OrderItem item = OrderItem.builder()
                .order(order).product(product).quantity(1).unitPrice(total).build();
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }
}
