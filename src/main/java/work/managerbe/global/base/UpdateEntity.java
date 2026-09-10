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
}
