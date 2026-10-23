package work.managerbe.oauthaccount.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** 약관의 본문과 버전을 보존하고 신규 가입에 사용할 버전의 활성 여부만 변경한다. */
@Entity
@Getter
@Table(name = "registration_terms", uniqueConstraints = @UniqueConstraint(columnNames = {"code", "version"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistrationTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 100)
    private String code;

    @Column(nullable = false, updatable = false, length = 50)
    private String version;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, updatable = false)
    private boolean required;

    @Column(nullable = false)
    private boolean active;

    /** 새 약관 버전을 활성 상태로 생성하며 이미 저장한 버전은 수정하지 않는다. */
    public static RegistrationTerm create(String code, String version, String title, String content, boolean required) {
        var term = new RegistrationTerm();
        term.code = code;
        term.version = version;
        term.title = title;
        term.content = content;
        term.required = required;
        term.active = true;
        return term;
    }

    /** 과거 본문과 버전은 보존하고 신규 가입용 목록에서 제외한다. */
    public void deactivate() { active = false; }
}
