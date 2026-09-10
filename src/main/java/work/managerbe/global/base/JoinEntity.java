package work.managerbe.global.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Objects;

@MappedSuperclass
@Getter
@ToString(callSuper = true)
public abstract class JoinEntity extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;

    /**
     * 최초 영속화 시 가입 시각을 설정한다.
     */
    @PrePersist
    protected void initializeJoinedAt() {
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
    }

    /**
     * 가입 이후의 탈퇴 시각을 기록하며 반복 호출에서는 최초 탈퇴 시각을 유지한다.
     */
    public void leave(LocalDateTime leftAt) {
        Objects.requireNonNull(leftAt, "탈퇴 시각은 필수입니다.");
        if (joinedAt == null || leftAt.isBefore(joinedAt)) {
            throw new IllegalArgumentException("탈퇴 시각은 가입 시각 이후여야 합니다.");
        }
        if (this.leftAt == null) {
            this.leftAt = leftAt;
        }
    }
}
