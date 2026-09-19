package work.managerbe.project.dto.response;

import java.util.List;

public record ProjectPageResponse(
        List<ProjectResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
