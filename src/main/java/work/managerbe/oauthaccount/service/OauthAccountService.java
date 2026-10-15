package work.managerbe.oauthaccount.service;

import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.user.domain.User;

import java.util.Optional;

public interface OauthAccountService {

    /** 소셜 식별자로 기존 회원만 조회하며 신규 회원은 생성하지 않는다. */
    Optional<User> findExisting(OAuthUserInfo userInfo);


    /**
     * 연결된 OAuth 계정의 사용자를 반환하고, 처음 로그인한 계정이면 사용자와 계정 연결을 생성한다.
     */
    User findOrCreate(OAuthUserInfo userInfo);
}
