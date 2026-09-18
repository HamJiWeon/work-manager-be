package work.managerbe.global.base;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@ToString(callSuper = true)
public abstract class UpdateEntity extends CreateEntity {

    @LastModifiedDate
    private LocalDateTime updatedAt;
    /**
     * 컬렉션 순서 변경처럼 일반 속성 변경이 없는 수정도 감사 시각에 반영한다.
     */
    protected void markUpdated() {
        this.updatedAt = LocalDateTime.now();
    }
}
