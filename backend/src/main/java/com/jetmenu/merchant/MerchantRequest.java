package com.jetmenu.merchant;

import com.jetmenu.validation.PasswordMatch;
import com.jetmenu.validation.ValidCnpj;
import jakarta.validation.constraints.*;
import lombok.*;

@PasswordMatch
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRequest {

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

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 8, max = 128, message = "Senha deve ter entre 8 e 128 caracteres")
    private String password;

    @NotBlank(message = "Confirmação de senha é obrigatória")
    @Size(min = 8, max = 128, message = "Confirmação de senha deve ter entre 8 e 128 caracteres")
    private String confirmPassword;

    @Size(max = 20, message = "Telefone não pode ter mais de 20 caracteres")
    @Pattern(regexp = "^[0-9+\\-() ]*$", message = "Telefone contém caracteres inválidos")
    private String phone;

    private String anotaAiApiKey;
}
