package work.managerbe.workspace.dto.response;

import java.time.LocalDateTime;

public record WorkspaceResponse(
        Long id,
        Long projectId,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
