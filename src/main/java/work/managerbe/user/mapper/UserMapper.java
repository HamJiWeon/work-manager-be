package work.managerbe.user.mapper;

import org.mapstruct.Mapper;
import work.managerbe.user.domain.User;
import work.managerbe.user.dto.response.UserResponse;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
