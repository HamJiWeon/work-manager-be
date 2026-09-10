# Spring Boot 프로젝트 지팀
AI 에이전트는 코드를 작성하거나 수정할 때 본 문서의 규칙을 반드시 따르세요.

## 수정 금지
- `.env`, `.env.local`은 읽거나 수정하지 않는다. API 키가 들어 있을 수 있다.

## 1. 빌드 명령어
작업을 완료했다고 말하기 전에 이 명령어를 실행한다.
```bash
./gradlew test
./gradlew clean build
```

## 2. 기술 스택 및 버전
| 구분                 | 기술                         | 버전                 |
|----------------------|------------------------------|----------------------|
| 언어                 | Java                         | 21                   |
| 백엔드 프레임워크    | Spring Boot                  | 4.1.1                |
| 웹 / REST API        | Spring Web MVC               | Spring Boot에서 관리 |
| 인증·인가            | Spring Security              | Spring Boot에서 관리 |
| ORM                  | Spring Data JPA / Hibernate  | Spring Boot에서 관리 |
| 데이터베이스         | PostgreSQL                   | 16                   |
| DB 드라이버          | PostgreSQL JDBC              | Spring Boot에서 관리 |
| 코드 간소화          | Lombok                       | Spring Boot에서 관리 |
| 빌드 도구            | Gradle                       | 9.7.1                |
| 의존성 관리 플러그인 | Spring Dependency Management | 1.1.7                |
| 테스트               | JUnit5 / Spring Boot Test    | Spring Boot에서 관리 |
| 로컬 DB 실행         | H2                           | Spring Boot에서 관리 |

## 4. 코딩 스타일 및 규칙
- **Lombok**: `@Getter`, `@RequiredArgsConstructor`를 적극 활용하며, `@Setter`는 사용하지 않는다.
- **의존성 주입**: 필드 주입 (`@Autowired`) 대신 **생성자 주입**(`1개의 생성자 및 final 필드`)을 사용한다.
- **예약어/명명 규칙**:
    - 클래스명은 PascalCase, 메서드와 변수명은 camelCase를 사용한다.
    - 엔티티 (`@Entity`) 클래스에는 비즈니스 로직 (메서드)을 포함할 수 있으나, 외부 DB 직접 제어 코드는 피한다.
- 생성자를 만들 때는 static factory method를 사용합니다. 
- **매직 넘버 금지**: 의미를 알 수 없는 상수는 `private static final` 상수로 선언하여 사용한다.
- 주석은 가급적 작성하지 않는다. 다만 cron 표현식, 정규식, javadoc, 테스트의 given/when/then 구분에는 사용할 수 있다.
- 코드 작성 후에 javadoc으로 어떤 로직을 사용해서 코드를 작성했는지 작성한다.

## 5. 테스트 전략 (Testing Strategy)
- 단위 테스트와 통합 테스트에 JUnit 5를 사용한다.
- 단위 테스트의 의존성 모킹에는 Mockito를 사용한다.
- Spring MVC 컨트롤러 테스트에는 @WebMvcTest(ControllerClass.class)를 사용한다.
- Spring 컨텍스트가 필요한 통합 테스트에는 @SpringBootTest를 사용한다.
- 테스트 메서드를 given/when/then 구조로 작성한다.
- 테스트 메서드 이름은 snake_case를 사용한다.
  - 예: get_user_by_id_ok, get_user_by_id_not_found_ko
- 테스트에서 리플렉션 사용을 피한다.
- 테스트에 비즈니스 로직을 넣지 말고, 동작 검증에 집중한다.


## DB 마이그레이션 규칙
- [README.md](src/main/resources/db/migration/README.md) 읽고 적용한다.

## 코드 리뷰 규칙
- 코드 리뷰를 요청받으면 Open Code Review의 위임 모드(delegate)를 사용한다.
- 별도 API 키 없이 Codex 모델로 리뷰하고, 결과는 한국어로 작성한다.
- 명시적으로 요청하지 않는 한 코드는 수정하지 않는다.