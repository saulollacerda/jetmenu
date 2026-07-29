package com.jetmenu.dashboard;

import com.jetmenu.auth.AuthHelper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuthHelper authHelper;

    public DashboardController(DashboardService dashboardService, AuthHelper authHelper) {
        this.dashboardService = dashboardService;
        this.authHelper = authHelper;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID merchantId = authHelper.getMerchantId(auth);
        DashboardResponse response = dashboardService.getDashboard(merchantId, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/peak-hours")
    public ResponseEntity<Map<String, List<PeakHour>>> peakHours(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(Map.of("items", dashboardService.peakHours(merchantId, startDate, endDate)));
    }

    @GetMapping("/ingredient-ranking")
    public ResponseEntity<List<IngredientConsumption>> ingredientRanking(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(dashboardService.ingredientRanking(merchantId, startDate, endDate));
    }

    @GetMapping("/channels")
    public ResponseEntity<List<ChannelBreakdown>> channels(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(dashboardService.channels(merchantId, startDate, endDate));
    }
}
