package work.managerbe.user.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.user.dto.response.UserResponse;
import work.managerbe.user.service.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    @AfterEach
    void securityContext를_정리한다() {
        SecurityContextHolder.clearContext();
    }

    /**
     * principal의 내부 UUID를 서비스에 전달하고 현재 사용자 정보를 반환하는지 검증한다.
     */
    @Test
    void 인증된_현재_사용자_정보를_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 9, 24, 10, 0);
        UserResponse response = new UserResponse(
                userId,
                "홍길동",
                "user@example.com",
                "https://example.com/profile.png",
                now,
                now
        );
        authenticate(userId);
        when(userService.getMe(userId)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.profileImgUrl").value("https://example.com/profile.png"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-24T10:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-24T10:00:00"));

        verify(userService).getMe(userId);
    }

    /**
     * principal의 UUID와 일치하는 사용자가 없으면 404 응답을 반환하는지 검증한다.
     */
    @Test
    void 현재_사용자가_없으면_404를_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        when(userService.getMe(userId))
                .thenThrow(UserException.of(UserErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));

        verify(userService).getMe(userId);
    }

    private static void authenticate(UUID userId) {
        var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
