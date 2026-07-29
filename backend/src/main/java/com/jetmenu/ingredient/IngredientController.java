package com.jetmenu.ingredient;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.product.IngredientProductUsageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {

    private final IngredientService ingredientService;
    private final AuthHelper authHelper;

    public IngredientController(IngredientService ingredientService, AuthHelper authHelper) {
        this.ingredientService = ingredientService;
        this.authHelper = authHelper;
    }

    @PostMapping
    public ResponseEntity<IngredientResponse> create(Authentication auth, @Valid @RequestBody IngredientRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        IngredientResponse response = ingredientService.create(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IngredientResponse> findById(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        IngredientResponse response = ingredientService.findById(merchantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<IngredientResponse>> findAll(
            Authentication auth,
            @RequestParam(required = false, defaultValue = "") String search,
            @PageableDefault(size = 20, sort = "position") Pageable pageable) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(ingredientService.findAll(merchantId, search, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IngredientResponse> update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody IngredientRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        IngredientResponse response = ingredientService.update(merchantId, id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Atualização atômica dos campos relacionados ao custo manual do ingrediente.
     * Não toca em {@code salePrice} (sincronizado do Anota.AI).
     */
    @PutMapping("/{id}/cost")
    public ResponseEntity<IngredientResponse> updateCost(Authentication auth, @PathVariable UUID id, @Valid @RequestBody IngredientCostRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        IngredientResponse response = ingredientService.updateCost(merchantId, id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/usages")
    public ResponseEntity<List<IngredientProductUsageResponse>> fetchUsages(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(ingredientService.fetchUsages(merchantId, id));
    }

    /**
     * Reordena manualmente o ingrediente para uma nova posição global (zero-based) na
     * ordenação padrão do merchant. Corpo: {@code {"position": <int>}}.
     */
    @PatchMapping("/{id}/position")
    public ResponseEntity<Void> reorder(Authentication auth, @PathVariable UUID id,
                                        @Valid @RequestBody IngredientPositionRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        ingredientService.reorder(merchantId, id, request.getPosition());
        return ResponseEntity.noContent().build();
    }

    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        ingredientService.delete(merchantId, id);
        return ResponseEntity.noContent().build();
    }
}
