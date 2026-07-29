package com.jetmenu.export;

import com.jetmenu.auth.AuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExportController.class)
@WithMockUser
@DisplayName("ExportController")
class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportService exportService;

    @MockitoBean
    private AuthHelper authHelper;

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @BeforeEach
    void setUp() {
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(UUID.randomUUID());
    }

    // -------------------------------------------------------------------------
    // GET /api/export/dashboard
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/export/dashboard")
    class GetExportDashboard {

        @Test
        @DisplayName("deve retornar 200 com content-type xlsx")
        void shouldReturn200WithXlsxContentType() throws Exception {
            byte[] fakeXlsx = new byte[]{1, 2, 3};
            given(exportService.generateDashboardExport(any(), any(), any())).willReturn(fakeXlsx);

            mockMvc.perform(get("/api/export/dashboard")
                            .param("startDate", "2026-03-01")
                            .param("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE)));
        }

        @Test
        @DisplayName("deve retornar Content-Disposition com attachment e filename")
        void shouldReturnContentDispositionWithAttachment() throws Exception {
            byte[] fakeXlsx = new byte[]{1, 2, 3};
            given(exportService.generateDashboardExport(any(), any(), any())).willReturn(fakeXlsx);

            mockMvc.perform(get("/api/export/dashboard")
                            .param("startDate", "2026-03-01")
                            .param("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            containsString("attachment")))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            containsString(".xlsx")));
        }

        @Test
        @DisplayName("deve passar startDate e endDate ao service como LocalDate")
        void shouldPassDateParamsToService() throws Exception {
            byte[] fakeXlsx = new byte[]{1, 2, 3};
            given(exportService.generateDashboardExport(any(), eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 31)))).willReturn(fakeXlsx);

            mockMvc.perform(get("/api/export/dashboard")
                            .param("startDate", "2026-03-01")
                            .param("endDate", "2026-03-31"))
                    .andExpect(status().isOk());

            then(exportService).should().generateDashboardExport(any(), eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 31)));
        }

        @Test
        @DisplayName("deve aceitar requisição sem parâmetros de data")
        void shouldAcceptRequestWithoutDateParams() throws Exception {
            byte[] fakeXlsx = new byte[]{1, 2, 3};
            given(exportService.generateDashboardExport(any(), isNull(), isNull())).willReturn(fakeXlsx);

            mockMvc.perform(get("/api/export/dashboard"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("deve retornar corpo com o byte array do service")
        void shouldReturnByteArrayFromService() throws Exception {
            byte[] fakeXlsx = new byte[]{10, 20, 30};
            given(exportService.generateDashboardExport(any(), any(), any())).willReturn(fakeXlsx);

            mockMvc.perform(get("/api/export/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(fakeXlsx));
        }
    }
}
