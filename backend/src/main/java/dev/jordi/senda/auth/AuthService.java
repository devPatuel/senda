package dev.jordi.senda.auth;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.category.DefaultCategories;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.ForbiddenException;
import dev.jordi.senda.common.JwtService;
import dev.jordi.senda.investment.AssetClass;
import dev.jordi.senda.investment.AssetClassRepository;
import dev.jordi.senda.investment.DefaultAssetClasses;
import dev.jordi.senda.user.User;
import dev.jordi.senda.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AssetClassRepository assetClassRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final boolean registrationEnabled;

    public AuthService(UserRepository userRepository, CategoryRepository categoryRepository,
                       AssetClassRepository assetClassRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       @Value("${senda.auth.registration-enabled:false}") boolean registrationEnabled) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.assetClassRepository = assetClassRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.registrationEnabled = registrationEnabled;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Closed by default: an instance reachable by anyone on the network must not
        // hand out accounts unless its operator opened the door on purpose.
        if (!registrationEnabled) {
            throw new ForbiddenException("Registration is closed");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }
        User user = userRepository.save(
                new User(request.email(), passwordEncoder.encode(request.password()), request.name()));
        copyDefaultCategories(user.getId());
        copyDefaultAssetClasses(user.getId());
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

    private void copyDefaultAssetClasses(Long userId) {
        List<AssetClass> assetClasses = DefaultAssetClasses.ALL.stream()
                .map(definition -> new AssetClass(userId, definition.name(), definition.pricingSource()))
                .toList();
        assetClassRepository.saveAll(assetClasses);
    }
}
