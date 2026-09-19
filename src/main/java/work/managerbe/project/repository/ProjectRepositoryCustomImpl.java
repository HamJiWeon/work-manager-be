package work.managerbe.project.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import work.managerbe.member.domain.QMember;
import work.managerbe.project.domain.Project;
import work.managerbe.project.domain.QProject;

@RequiredArgsConstructor
public class ProjectRepositoryCustomImpl implements ProjectRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Project> findActiveProjects(UUID userId, Pageable pageable) {
        QProject project = QProject.project;
        QMember member = QMember.member;

        BooleanBuilder builder = new BooleanBuilder()
                .and(member.user.id.eq(userId))
                .and(member.leftAt.isNull());

        return queryFactory
                .select(project)
                .from(member)
                .join(member.project, project)
                .where(builder)
                .orderBy(
                        project.name.asc(),
                        project.createdAt.desc(),
                        project.id.asc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    @Override
    public long countActiveProjects(UUID userId) {
        QMember member = QMember.member;

        Long total = queryFactory
                .select(member.count())
                .from(member)
                .where(
                        member.user.id.eq(userId),
                        member.leftAt.isNull()
                )
                .fetchOne();

        return total == null ? 0L : total;
    }
}