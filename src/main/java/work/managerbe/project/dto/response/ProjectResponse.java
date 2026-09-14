package work.managerbe.project.dto.response;

import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        String code,
        String name,
        long nextCardNumber,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
