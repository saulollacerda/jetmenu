package com.jetmenu.auth;

import com.jetmenu.identity.Identity;
import com.jetmenu.identity.IdentityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthHelper")
class AuthHelperTest {

    @Mock
    private IdentityRepository identityRepository;

    @InjectMocks
    private AuthHelper authHelper;

    @Test
    @DisplayName("deve resolver o merchantId a partir do uuid no principal")
    void getMerchantId_shouldResolveFromPrincipalUuid() {
        String uuid = UUID.randomUUID().toString();
        UUID merchantId = UUID.randomUUID();
        given(identityRepository.findByProviderAndProviderUserId("supabase", uuid))
                .willReturn(Optional.of(Identity.builder().merchantId(merchantId).build()));

        UUID result = authHelper.getMerchantId(authentication(uuid));

        assertThat(result).isEqualTo(merchantId);
    }

    @Test
    @DisplayName("deve lançar 403 quando não há identity para o uuid")
    void getMerchantId_shouldThrowForbiddenWhenNotProvisioned() {
        String uuid = UUID.randomUUID().toString();
        given(identityRepository.findByProviderAndProviderUserId("supabase", uuid))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authHelper.getMerchantId(authentication(uuid)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    private Authentication authentication(String uuid) {
        return new TestingAuthenticationToken(uuid, null);
    }
}
