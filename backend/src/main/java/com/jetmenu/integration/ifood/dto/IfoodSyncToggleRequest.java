package com.jetmenu.integration.ifood.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IfoodSyncToggleRequest {

    @NotNull
    private Boolean enabled;
}
