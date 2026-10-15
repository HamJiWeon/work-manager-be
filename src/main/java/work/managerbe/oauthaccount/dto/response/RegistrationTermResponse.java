package work.managerbe.oauthaccount.dto.response;

import work.managerbe.oauthaccount.domain.RegistrationTerm;

/** 프론트엔드가 표시하고 다시 제출할 약관 식별자·버전·필수 여부를 제공한다. */
public record RegistrationTermResponse(String id, String title, String version, String content, boolean required) {
    /** 내부 UUID 대신 약관 코드를 외부 식별자로 사용한다. */
    public static RegistrationTermResponse from(RegistrationTerm term) {
        return new RegistrationTermResponse(term.getCode(), term.getTitle(), term.getVersion(), term.getContent(), term.isRequired());
    }
}
