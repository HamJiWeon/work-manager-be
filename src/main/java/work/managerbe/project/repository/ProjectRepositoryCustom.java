package work.managerbe.project.repository;

import org.springframework.data.domain.Pageable;
import work.managerbe.project.domain.Project;

import java.util.List;
import java.util.UUID;

public interface ProjectRepositoryCustom {

    List<Project> findActiveProjects(UUID userId, Pageable pageable);

    long countActiveProjects(UUID userId);
}
