package work.managerbe.project.dto.request;

public record ProjectCreateRequest(
        String code,
        String name,
        String description
) {
}
