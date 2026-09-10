package work.managerbe.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.global.base.UpdateEntity;

import java.util.UUID;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends UpdateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String email;

    private String profileImgUrl;

    @Builder
    private User(String name, String email, String profileImgUrl) {
        this.name = name;
        this.email = email;
        this.profileImgUrl = profileImgUrl;
    }

    public static User create(String name, String email, String profileImgUrl) {
        return User.builder()
                .name(name)
                .email(email)
                .profileImgUrl(profileImgUrl)
                .build();
    }

}
