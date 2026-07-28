package com.jetmenu.auth;

import com.jetmenu.validation.ValidCnpj;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Business data + credentials for the dev-only local registration
 * ({@code POST /api/auth/dev-register}). Mirrors {@link ProvisionRequest} plus a password,
 * since here JetMenu owns the credentials (no Supabase).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevRegisterRequest {

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

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 6, message = "Senha deve ter ao menos 6 caracteres")
    private String password;
}
