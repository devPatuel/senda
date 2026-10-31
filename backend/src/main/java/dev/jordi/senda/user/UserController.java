package dev.jordi.senda.user;

import dev.jordi.senda.common.CurrentUser;
import dev.jordi.senda.common.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Lets the client adapt its menu; the server still checks every admin request. */
    @GetMapping("/me")
    public MeResponse me() {
        User user = userRepository.findById(CurrentUser.id())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.isAdmin());
    }
}
