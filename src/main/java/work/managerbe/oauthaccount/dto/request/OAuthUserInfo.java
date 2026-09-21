package work.managerbe.oauthaccount.dto.request;

public record OAuthUserInfo(
        String provider,
        String providerUserId,
        String name,
        String email,
        String profileImageUrl
) {
}
