package work.managerbe.oauthaccount.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record OauthAccountResponse(
        Long id,
        UUID userId,
        String provider,
        String providerUserId,
        LocalDateTime createdAt
) {
}
