package com.jetmenu.integration.ifood;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ifood_app_token")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IfoodAppToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String accessToken;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String refreshToken;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime refreshExpiresAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
