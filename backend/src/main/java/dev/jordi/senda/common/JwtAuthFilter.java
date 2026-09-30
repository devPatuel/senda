package dev.jordi.senda.common;

import dev.jordi.senda.apitoken.ApiTokenService;
import dev.jordi.senda.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /** A full session: the user logged in with their password. */
    public static final String ROLE_USER = "ROLE_USER";
    /** A personal access token: limited to the capture endpoints (see SecurityConfig). */
    public static final String ROLE_TOKEN = "ROLE_TOKEN";

    private final JwtService jwtService;
    private final ApiTokenService apiTokenService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, ApiTokenService apiTokenService,
                         UserRepository userRepository) {
        this.jwtService = jwtService;
        this.apiTokenService = apiTokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String credential = header.substring(BEARER_PREFIX.length());
            // Personal access tokens carry a fixed prefix; everything else is a JWT.
            boolean personalToken = credential.startsWith(ApiTokenService.PREFIX);
            Optional<Long> userId = personalToken
                    ? apiTokenService.resolveUserId(credential)
                    : currentSessionUserId(credential);
            String role = personalToken ? ROLE_TOKEN : ROLE_USER;
            userId.ifPresent(id -> {
                // Principal is the user id (Long); read it back with CurrentUser.id()
                var authentication = new UsernamePasswordAuthenticationToken(
                        id, null, List.of(new SimpleGrantedAuthority(role)));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }

    /**
     * A JWT stays valid until it expires, so a signature check alone would keep a
     * stolen session alive after the owner changes the password. Comparing the
     * token's version with the stored one closes every earlier session at once.
     */
    private Optional<Long> currentSessionUserId(String jwt) {
        return jwtService.parse(jwt)
                .filter(session -> userRepository.findTokenVersionById(session.userId())
                        .filter(current -> current == session.tokenVersion())
                        .isPresent())
                .map(JwtService.Session::userId);
    }
}
