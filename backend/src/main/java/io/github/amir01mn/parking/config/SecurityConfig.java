package io.github.amir01mn.parking.config;

import io.github.amir01mn.parking.sensor.SensorKeyAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT resource server. URL rules follow the API table in {@code docs/PLAN.md}; roles are hierarchical
 * (see {@link JwtConfig#roleHierarchy()}), so {@code hasRole("USER")} also admits managers.
 *
 * <p>{@code /api/sensors/**} is not JWT-protected: {@link SensorKeyAuthenticationFilter} authenticates the
 * {@code X-Sensor-Key} header and grants {@code ROLE_SENSOR}, which no user role implies.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                                                   ProblemDetailAccessDeniedHandler accessDeniedHandler,
                                                   SensorProperties sensorProperties,
                                                   ProblemDetailWriter problemDetailWriter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health", "/actuator/health/**",
                                "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/lots", "/api/lots/**").permitAll()
                        .requestMatchers("/api/sensors/**").hasRole("SENSOR")
                        .requestMatchers("/api/admin/**").hasRole("SUPER_MANAGER")
                        .requestMatchers("/api/manager/**").hasRole("MANAGER")
                        .requestMatchers("/api/**").hasRole("USER")
                        .anyRequest().denyAll())
                .addFilterBefore(new SensorKeyAuthenticationFilter(sensorProperties.apiKey(), problemDetailWriter),
                        BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties cors) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Sensor-Key"));
        config.setExposedHeaders(List.of("Location"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
