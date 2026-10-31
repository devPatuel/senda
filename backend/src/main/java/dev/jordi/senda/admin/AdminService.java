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
        return userRepository.findByDeletedAtIsNull(Sort.by("createdAt", "id")).stream()
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
        activeUser(targetId).changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * Soft-deletes an account. Administrators are refused, the requester included: the
     * panel must not be able to remove the only way into itself.
     */
    @Transactional
    public void deleteUser(Long requesterId, Long targetId) {
        requireAdmin(requesterId);
        User target = activeUser(targetId);
        if (target.isAdmin()) {
            throw new UnprocessableEntityException("Administrator accounts cannot be deleted");
        }
        target.softDelete();
    }

    private User activeUser(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    // Read from the database on every call, so revoking the flag takes effect at once.
    private void requireAdmin(Long userId) {
        boolean admin = userRepository.findById(userId).map(User::isAdmin).orElse(false);
        if (!admin) {
            throw new ForbiddenException("Administrator access required");
        }
    }
}
