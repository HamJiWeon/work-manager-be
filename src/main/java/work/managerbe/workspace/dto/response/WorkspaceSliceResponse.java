package work.managerbe.workspace.dto.response;

import java.util.List;

public record WorkspaceSliceResponse(
        List<WorkspaceResponse> content,
        int page,
        int size,
        boolean hasPrevious,
        boolean hasNext
) {
}
