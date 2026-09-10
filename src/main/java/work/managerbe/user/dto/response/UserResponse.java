package work.managerbe.user.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String profileImgUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
