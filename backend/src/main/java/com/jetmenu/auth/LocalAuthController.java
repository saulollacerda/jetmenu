package com.jetmenu.auth;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only local auth endpoints (no Supabase). Registered only under the dev/test profiles;
 * in prod these beans do not exist and the paths 404. Both are public (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
@Profile({"dev", "test"})
public class LocalAuthController {

    private final LocalAuthService localAuthService;

    public LocalAuthController(LocalAuthService localAuthService) {
        this.localAuthService = localAuthService;
    }

    @PostMapping("/dev-register")
    public ResponseEntity<DevAuthResponse> register(@Valid @RequestBody DevRegisterRequest request) {
        return ResponseEntity.ok(localAuthService.register(request));
    }

    @PostMapping("/dev-login")
    public ResponseEntity<DevAuthResponse> login(@Valid @RequestBody DevLoginRequest request) {
        return ResponseEntity.ok(localAuthService.login(request));
    }
}
