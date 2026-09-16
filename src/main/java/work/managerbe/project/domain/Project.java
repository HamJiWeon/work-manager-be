package work.managerbe.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.global.base.BaseEntity;

@Entity
@Getter
@Table(name = "projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long nextCardNumber;

    private String description;

    @Builder
    private Project(String code, String name, long nextCardNumber, String description) {
        this.code = code;
        this.name = name;
        this.nextCardNumber = nextCardNumber;
        this.description = description;
    }

    public static Project create(String code, String name, String description) {
        return Project.builder()
                .code(code)
                .name(name)
                .nextCardNumber(1L)
                .description(description)
                .build();
    }

    public String cardPrefix(String code) {
        String[] prefix = code.split("_");
        return prefix[0];
    }
}
