package com.jetmenu.merchant;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

/**
 * Atualização de perfil do merchant autenticado (PUT /api/merchants/me).
 * Campos imutáveis (cnpj, email, password) NÃO entram aqui — exigem fluxos próprios
 * de segurança. Todos os campos são opcionais; valores null preservam o valor atual.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantUpdateRequest {

    @Size(max = 255, message = "Nome do restaurante não pode ter mais de 255 caracteres")
    private String merchantName;

    @Size(max = 20, message = "Telefone não pode ter mais de 20 caracteres")
    @Pattern(regexp = "^[0-9+\\-() ]*$", message = "Telefone contém caracteres inválidos")
    private String phone;

    @Size(max = 500, message = "Endereço não pode ter mais de 500 caracteres")
    private String address;

    @Size(max = 500, message = "URL do logo não pode ter mais de 500 caracteres")
    private String logoUrl;

    private List<OpeningHour> openingHours;
}
