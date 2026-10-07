package work.managerbe.workspace.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import work.managerbe.workspace.domain.QWorkspace;
import work.managerbe.workspace.domain.Workspace;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class WorkspaceRepositoryCustomImpl implements WorkspaceRepositoryCustom{

    private static final long NEXT_SLICE_LOOKAHEAD = 1L;

    private final JPAQueryFactory queryFactory;

    @Override
    public Slice<Workspace> findAllByProjectPath(UUID creatorId, String code, Pageable pageable) {
        QWorkspace workspace = QWorkspace.workspace;
        int pageSize = pageable.getPageSize();

        List<Workspace> fetched = queryFactory
                .selectFrom(workspace)
                .where(
                        workspace.project.creator.id.eq(creatorId),
                        workspace.project.code.eq(code)
                )
                .orderBy(
                        workspace.createdAt.desc(),
                        workspace.id.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageSize + NEXT_SLICE_LOOKAHEAD)
                .fetch();

        boolean hasNext = fetched.size() > pageSize;

        List<Workspace> content = hasNext
                ? fetched.subList(0, pageSize)
                : fetched;

        return new SliceImpl<>(content, pageable, hasNext);
    }
}
