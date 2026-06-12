package dev.jordi.senda.auth;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.category.DefaultCategories;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.JwtService;
import dev.jordi.senda.user.User;
import dev.jordi.senda.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User savedUser() {
        User user = new User("jordi@example.com", "hashed", "Jordi");
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }

    @Test
    void registerCreatesUserWithEncodedPasswordAndDefaultCategories() {
        RegisterRequest request = new RegisterRequest("jordi@example.com", "password123", "Jordi");
        when(userRepository.existsByEmail("jordi@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedUser());
        when(jwtService.generateToken(7L)).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo(7L);
        assertThat(response.user().email()).isEqualTo("jordi@example.com");
        assertThat(response.user().name()).isEqualTo("Jordi");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Category>> categoriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(categoriesCaptor.capture());
        List<Category> categories = categoriesCaptor.getValue();
        assertThat(categories).hasSize(DefaultCategories.ALL.size());
        assertThat(categories).allSatisfy(category -> {
            assertThat(category.getUserId()).isEqualTo(7L);
            assertThat(category.isActive()).isTrue();
        });
        assertThat(categories).extracting(Category::getName).contains("Comida", "Nómina");
    }

    @Test
    void registerWithDuplicateEmailThrowsConflict() {
        when(userRepository.existsByEmail("jordi@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("jordi@example.com", "password123", "Jordi")))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithValidCredentialsReturnsTokenAndUser() {
        when(userRepository.findByEmail("jordi@example.com")).thenReturn(Optional.of(savedUser()));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(7L)).thenReturn("jwt-token");

        AuthResponse response = authService.login(new LoginRequest("jordi@example.com", "password123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().email()).isEqualTo("jordi@example.com");
    }

    @Test
    void loginWithWrongPasswordThrowsBadCredentials() {
        when(userRepository.findByEmail("jordi@example.com")).thenReturn(Optional.of(savedUser()));
        when(passwordEncoder.matches("wrongpass", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("jordi@example.com", "wrongpass")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginWithUnknownEmailThrowsBadCredentials() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "password123")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
