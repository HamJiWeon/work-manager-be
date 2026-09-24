package work.managerbe.user.service;

import work.managerbe.user.dto.response.UserResponse;

import java.util.UUID;

public interface UserService {

    UserResponse getMe(UUID userId);
}
