package work.managerbe.workspace.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import work.managerbe.workspace.domain.Workspace;

import java.util.UUID;

public interface WorkspaceRepositoryCustom {

    Slice<Workspace> findAllByProjectPath(UUID creatorId, String code, Pageable pageable);
}
