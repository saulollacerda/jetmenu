package com.jetmenu.billing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final SubscriptionService subscriptionService;

    public PlanController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> listActivePlans() {
        return ResponseEntity.ok(subscriptionService.listActivePlans());
    }
}
