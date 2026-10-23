package work.managerbe.oauthaccount.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/** 클라이언트는 약관 식별자·버전·선택만 보내며 필수 여부와 기록 시각은 서버가 결정한다. */
public record RegistrationCompleteRequest(
        @NotEmpty @Size(max = MAX_CONSENTS) List<@NotNull @Valid Consent> consents) {
    private static final int MAX_CONSENTS = 100;

    public record Consent(@NotBlank String id, @NotBlank String version, @NotNull Boolean agreed) { }
}
