package dev.jordi.senda.admin;

import dev.jordi.senda.common.ForbiddenException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.UnprocessableEntityException;
import dev.jordi.senda.user.User;
import dev.jordi.senda.user.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers(Long requesterId) {
        requireAdmin(requesterId);
        return userRepository.findAll(Sort.by("createdAt", "id")).stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    /**
     * Replaces another user's password without knowing the current one and closes
     * their open sessions. The admin's own password goes through the normal change,
     * which asks for the current one: a stolen admin session must not be able to
     * lock the real admin out.
     */
    @Transactional
    public void resetPassword(Long requesterId, Long targetId, ResetPasswordRequest request) {
        requireAdmin(requesterId);
        if (requesterId.equals(targetId)) {
            throw new UnprocessableEntityException("Use the password page to change your own password");
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        target.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    // Read from the database on every call, so revoking the flag takes effect at once.
    private void requireAdmin(Long userId) {
        boolean admin = userRepository.findById(userId).map(User::isAdmin).orElse(false);
        if (!admin) {
            throw new ForbiddenException("Administrator access required");
        }
    }
}
