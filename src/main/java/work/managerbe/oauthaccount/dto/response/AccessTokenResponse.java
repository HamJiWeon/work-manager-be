package work.managerbe.oauthaccount.dto.response;

public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    private static final String BEARER = "Bearer";

    public static AccessTokenResponse of(String accessToken, long expiresIn) {
        return new AccessTokenResponse(accessToken, BEARER, expiresIn);
    }
}
