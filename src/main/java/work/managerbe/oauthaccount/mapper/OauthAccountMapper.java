package work.managerbe.oauthaccount.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import work.managerbe.oauthaccount.domain.OauthAccount;
import work.managerbe.oauthaccount.dto.response.OauthAccountResponse;

@Mapper(componentModel = "spring")
public interface OauthAccountMapper {

    @Mapping(source = "user.id", target = "userId")
    OauthAccountResponse toResponse(OauthAccount oauthAccount);
}
