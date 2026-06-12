package dev.jordi.senda.auth;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.category.DefaultCategories;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.JwtService;
import dev.jordi.senda.user.User;
import dev.jordi.senda.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, CategoryRepository categoryRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }
        User user = userRepository.save(
                new User(request.email(), passwordEncoder.encode(request.password()), request.name()));
        copyDefaultCategories(user.getId());
        return new AuthResponse(jwtService.generateToken(user.getId()), UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Same exception for unknown email and wrong password: do not reveal which one failed
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return new AuthResponse(jwtService.generateToken(user.getId()), UserDto.from(user));
    }

    private void copyDefaultCategories(Long userId) {
        List<Category> categories = DefaultCategories.ALL.stream()
                .map(definition -> new Category(userId, definition.name(), definition.type(), definition.color()))
                .toList();
        categoryRepository.saveAll(categories);
    }
}
