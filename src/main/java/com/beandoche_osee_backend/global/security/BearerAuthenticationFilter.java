package com.beandoche_osee_backend.global.security;

import java.io.IOException;
import java.util.List;

import com.beandoche_osee_backend.domain.auth.entity.User;
import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenClaims;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenProvider;
import com.beandoche_osee_backend.domain.auth.token.InvalidAccessTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenProvider accessTokenProvider;
    private final UserRepository userRepository;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public BearerAuthenticationFilter(
            AccessTokenProvider accessTokenProvider,
            UserRepository userRepository,
            RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.accessTokenProvider = accessTokenProvider;
        this.userRepository = userRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null && SecurityContextHolder.getContext().getAuthentication() == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String rawToken = extractBearerToken(authorization);
            AccessTokenClaims claims = accessTokenProvider.parse(rawToken);
            User user = userRepository.findById(claims.userId())
                    .filter(User::isActive)
                    .orElseThrow(InvalidAccessAuthenticationException::new);
            setAuthentication(request, user);
            filterChain.doFilter(request, response);
        } catch (InvalidAccessTokenException | InvalidAccessAuthenticationException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new InvalidAccessAuthenticationException());
        }
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new InvalidAccessAuthenticationException();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new InvalidAccessAuthenticationException();
        }
        return token;
    }

    private void setAuthentication(HttpServletRequest request, User user) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(user.getId());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
