package com.rentapi.rentapi.security;

import com.rentapi.rentapi.model.Usuario;
import com.rentapi.rentapi.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-Key";

    private final AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(HEADER);

        if (apiKey != null && !apiKey.isBlank()) {
            Usuario usuario = authService.findByApiKey(apiKey);

            if (usuario != null) {
                // Guardem l'usuari a l'atribut de la request per poder-lo llegir des dels controllers
                request.setAttribute("usuario", usuario);

                // Autentiquem a Spring Security
                var auth = new UsernamePasswordAuthenticationToken(
                        usuario.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPlan().toUpperCase()))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            // Si la clau existeix però no és vàlida, no autentiquem — Spring Security
            // retornarà 401 si l'endpoint ho requereix
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Rutes públiques que no necessiten API Key
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/register")
                || path.startsWith("/api/v1/geo/")
                || path.startsWith("/api/v1/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}
