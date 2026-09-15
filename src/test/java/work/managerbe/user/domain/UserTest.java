package work.managerbe.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("사용자를 생성하면 이름, 이메일과 프로필 이미지가 저장된다.")
    void 사용자_생성() {
        // given
        String name = "홍길동";
        String email = "user@example.com";
        String profileImgUrl = "https://example.com/profile.png";

        // when
        User user = User.create(name, email, profileImgUrl);

        // then
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getProfileImgUrl()).isEqualTo(profileImgUrl);
        assertThat(user.getId()).isNull();
        assertThat(user.getCreatedAt()).isNull();
        assertThat(user.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("이메일과 프로필 이미지 없이 사용자를 생성할 수 있다.")
    void 선택_정보_없이_사용자_생성() {
        // given
        String name = "홍길동";

        // when
        User user = User.create(name, null, null);

        // then
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getProfileImgUrl()).isNull();
    }
}