package work.managerbe.oauthaccount.dto.request;

import work.managerbe.oauthaccount.domain.OAuthProvider;

public record OAuthUserInfo(
        OAuthProvider provider,
        String providerUserId,
        String name,
        String email,
        String profileImageUrl
) {
}
