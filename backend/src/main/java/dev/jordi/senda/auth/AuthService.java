package dev.jordi.senda.auth;

import dev.jordi.senda.common.UnprocessableEntityException;
import java.util.UUID;
import java.util.Optional;
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
    // A real hash of a value nobody knows: login compares against it when the email
    // does not exist, so both outcomes cost one BCrypt check and take the same time.
    private final String dummyPasswordHash;

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
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
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
        return sessionFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Same exception AND same work for unknown email and wrong password: neither
        // the message nor the response time reveals whether the account exists.
        Optional<User> found = userRepository.findByEmail(request.email());
        String hash = found.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (found.isEmpty() || !matches) {
            throw new BadCredentialsException("Invalid credentials");
        }
        User user = found.get();
        return sessionFor(user);
    }

    /**
     * Changes the password of the authenticated user. Every session opened before
     * the change stops working; the response carries a fresh one so the device
     * that made the change stays logged in.
     */
    @Transactional
    public AuthResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        // 422, not 401: the client treats a 401 as an expired session and logs out
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnprocessableEntityException("Current password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        return sessionFor(user);
    }

    private AuthResponse sessionFor(User user) {
        return new AuthResponse(jwtService.generateToken(user.getId(), user.getTokenVersion()), UserDto.from(user));
    }

    private void copyDefaultCategories(Long userId) {
        List<Category> categories = DefaultCategories.ALL.stream()
                .map(definition -> {
                    Category category = new Category(userId, definition.name(), definition.type(), definition.color());
                    category.setFixed(definition.fixed());
                    return category;
                })
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
