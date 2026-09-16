package work.managerbe.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import work.managerbe.project.domain.Project;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("""
        select m.project
        from Member m
        where m.user.id = :userId
          and m.leftAt is null
        order by m.project.id asc
        """)
    List<Project> findByUser_Id(@Param("userId") UUID userId);
}
