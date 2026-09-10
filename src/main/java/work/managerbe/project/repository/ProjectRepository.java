package work.managerbe.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.project.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
