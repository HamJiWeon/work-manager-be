package work.managerbe.project.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import work.managerbe.project.domain.Project;

import java.util.UUID;

public interface ProjectRepositoryCustom {

    Slice<Project> findActiveProjects(UUID userId, Pageable pageable);

}
