package com.jetmenu.auth;

import com.jetmenu.validation.ValidCnpj;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Payload sent by the frontend right after signing up in Supabase, carrying the
 * merchant's business data. The Supabase user id (and credentials) live in Supabase;
 * the email is echoed here so the merchant row can store it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvisionRequest {

    @NotBlank(message = "Nome do restaurante é obrigatório")
    @Size(max = 255, message = "Nome do restaurante não pode ter mais de 255 caracteres")
    private String merchantName;

    @NotBlank(message = "CNPJ é obrigatório")
    @ValidCnpj
    private String cnpj;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    @Size(max = 255, message = "Email não pode ter mais de 255 caracteres")
    private String email;

    @Size(max = 20, message = "Telefone não pode ter mais de 20 caracteres")
    @Pattern(regexp = "^[0-9+\\-() ]*$", message = "Telefone contém caracteres inválidos")
    private String phone;
}
