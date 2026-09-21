package work.managerbe.oauthaccount.service;

import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.user.domain.User;

public interface OauthAccountService {

    User findOrCreate(OAuthUserInfo userInfo);
}
