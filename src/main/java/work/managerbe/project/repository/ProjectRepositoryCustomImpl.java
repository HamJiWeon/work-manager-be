package work.managerbe.project.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import work.managerbe.member.domain.QMember;
import work.managerbe.project.domain.Project;
import work.managerbe.project.domain.QProject;

@RequiredArgsConstructor
public class ProjectRepositoryCustomImpl implements ProjectRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Slice<Project> findActiveProjects(UUID userId, Pageable pageable) {
        QProject project = QProject.project;
        QMember member = QMember.member;

        List<Project> fetched = queryFactory
                .selectDistinct(project)
                .from(member)
                .join(member.project, project)
                .where(
                        member.user.id.eq(userId),
                        member.leftAt.isNull()
                )
                .orderBy(
                        project.name.asc(),
                        project.createdAt.desc(),
                        project.id.asc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)
                .fetch();

        boolean hasNext =
                fetched.size() > pageable.getPageSize();

        List<Project> content = hasNext
                ? fetched.subList(0, pageable.getPageSize())
                : fetched;

        return new SliceImpl<>(content, pageable, hasNext);
    }
}