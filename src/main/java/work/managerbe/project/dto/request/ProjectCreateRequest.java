package work.managerbe.project.dto.request;

public record ProjectCreateRequest(
        String name,
        String cardPrefix,
        String description
) {
}
