package work.managerbe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OAuth 프로필과 OAuth 환경변수 없이 실제 main 메서드로 기본 애플리케이션이 기동하는지 검증한다.
 */
@SpringBootTest(useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
class ManagerBeApplicationTests {

    @Test
    void 메인_메서드로_애플리케이션이_정상_기동한다(
            @Autowired ConfigurableApplicationContext context) {
        // given / when
        // then
        assertThat(context.isActive()).isTrue();
        assertThat(context.getBean(ManagerBeApplication.class)).isNotNull();
    }

}
