package dev.jordi.senda.auth;

import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.ForbiddenException;
import dev.jordi.senda.common.GlobalExceptionHandler;
import dev.jordi.senda.common.JwtAuthFilter;
import dev.jordi.senda.apitoken.ApiTokenService;
import dev.jordi.senda.common.JwtService;
import dev.jordi.senda.common.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @MockitoBean
    private ApiTokenService apiTokenService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    private static final AuthResponse RESPONSE =
            new AuthResponse("jwt-token", new UserDto(1L, "jordi@example.com", "Jordi"));

    @Test
    void registerReturns201WithTokenAndUser() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(RESPONSE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jordi@example.com", "password": "password123", "name": "Jordi"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.email").value("jordi@example.com"))
                .andExpect(jsonPath("$.user.name").value("Jordi"));
    }

    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ConflictException("Email already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jordi@example.com", "password": "password123", "name": "Jordi"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email already registered"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void registerWithRegistrationClosedReturns403() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ForbiddenException("Registration is closed"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "intruder@example.com", "password": "password123", "name": "Intruder"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Registration is closed"));
    }

    @Test
    void registerWithInvalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "short", "name": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void loginReturns200WithToken() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(RESPONSE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jordi@example.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.user.email").value("jordi@example.com"));
    }

    @Test
    void loginWithBadCredentialsReturns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jordi@example.com", "password": "wrongpass1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // --- BCrypt 72-byte limit: must be a 400 validation error, never a 500 ---

    @Test
    void registerWithPasswordOver72BytesReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jordi@example.com", "password": "%s", "name": "Jordi"}
                                """.formatted("a".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        verifyNoInteractions(authService);
    }

    @Test
    void registerWithMultibytePasswordOver72BytesReturns400() throws Exception {
        // 40 characters but 80 UTF-8 bytes: BCrypt's limit is bytes, not characters
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jordi@example.com", "password": "%s", "name": "Jordi"}
                                """.formatted("ñ".repeat(40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        verifyNoInteractions(authService);
    }

    @Test
    void registerWithPasswordOfExactly72BytesIsAccepted() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(RESPONSE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jordi@example.com", "password": "%s", "name": "Jordi"}
                                """.formatted("a".repeat(72))))
                .andExpect(status().isCreated());
    }

    @Test
    void loginWithPasswordOver72BytesReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jordi@example.com", "password": "%s"}
                                """.formatted("a".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        verifyNoInteractions(authService);
    }

    // --- check-then-act race: DB constraint violation must map to 409, not 500 ---

    @Test
    void registerRaceHittingDbUniqueConstraintReturns409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jordi@example.com", "password": "password123", "name": "Jordi"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Resource conflict, please retry"));
    }
}
