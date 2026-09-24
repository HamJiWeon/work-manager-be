package work.managerbe.oauthaccount.service;

import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.user.domain.User;

public interface OauthAccountService {

    /**
     * 연결된 OAuth 계정의 사용자를 반환하고, 처음 로그인한 계정이면 사용자와 계정 연결을 생성한다.
     */
    User findOrCreate(OAuthUserInfo userInfo);
}
