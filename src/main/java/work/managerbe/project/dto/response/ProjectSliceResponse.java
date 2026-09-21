package work.managerbe.project.dto.response;

import java.util.List;

public record ProjectSliceResponse(
        List<ProjectResponse> content,
        int page,
        int size,
        boolean hasPrevious,
        boolean hasNext
) {
}
