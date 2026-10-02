package work.managerbe.oauthaccount.dto.response;

import work.managerbe.oauthaccount.domain.OAuthProvider;

import java.time.LocalDateTime;
import java.util.UUID;

public record OauthAccountResponse(
        Long id,
        UUID userId,
        OAuthProvider provider,
        String providerUserId,
        LocalDateTime createdAt
) {
}
