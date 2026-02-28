package np.edu.nast.payroll.Payroll.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import np.edu.nast.payroll.Payroll.security.CustomUserDetailsService;
import np.edu.nast.payroll.Payroll.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final CustomUserDetailsService userDetailsService;

    @Bean
    static GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults("");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // This replaces NoOpPasswordEncoder and enables secure BCrypt hashing
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        provider.setHideUserNotFoundExceptions(false);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        /* 1. PUBLIC PERMISSIONS (Always First) */
                        .requestMatchers("/photos/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**", "/error", "/favicon.ico").permitAll()
                        .requestMatchers("/api/esewa/success/**", "/api/esewa/failure/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/leave-types/**", "/api/leave-balance/**").permitAll()
                        .requestMatchers("/api/users/forgot-password").permitAll()
                        .requestMatchers("/api/users/reset-password").permitAll() // You'll likely need this next

                        /* 2. SYSTEM CONFIGURATION (Strict Admin/Accountant) */
                        // Added this as a high priority rule
                        .requestMatchers("/api/system-config/**")
                        .hasAnyAuthority("ADMIN", "ACCOUNTANT", "ROLE_ADMIN", "ROLE_ACCOUNTANT")

                        /* 3. TAX SLABS ACCESS */
                        .requestMatchers(HttpMethod.GET, "/api/tax-slabs/**")
                        .hasAnyAuthority("ADMIN", "ACCOUNTANT", "EMPLOYEE", "ROLE_ADMIN", "ROLE_ACCOUNTANT", "ROLE_EMPLOYEE")
                        .requestMatchers("/api/tax-slabs/**")
                        .hasAnyAuthority("ADMIN", "ACCOUNTANT", "ROLE_ADMIN", "ROLE_ACCOUNTANT")

                        /* 4. ATTENDANCE & LEAVES (Specific methods first) */
                        .requestMatchers(HttpMethod.POST, "/api/attendance/**").hasAnyAuthority("ADMIN", "ROLE_ADMIN", "EMPLOYEE", "ROLE_EMPLOYEE","ACCOUNTANT","ROLE_ACCOUNTANT")
                        .requestMatchers("/api/attendance/**", "/api/employee-leaves/**", "/api/salary-analytics/**")
                        .hasAnyAuthority("ADMIN", "ROLE_ADMIN", "ACCOUNTANT", "ROLE_ACCOUNTANT", "EMPLOYEE", "ROLE_EMPLOYEE")

                        /* 5. PAYROLL & FINANCIALS */
                        .requestMatchers("/api/payrolls/**", "/api/esewa/initiate/**", "/api/holidays/**", "/api/reports/**")
                        .hasAnyAuthority("ADMIN", "ACCOUNTANT", "ROLE_ADMIN", "ROLE_ACCOUNTANT")

                        /* 6. DASHBOARD */
                        .requestMatchers("/api/employee/dashboard/**")
                        .hasAnyAuthority("ADMIN", "ACCOUNTANT", "EMPLOYEE", "ROLE_ADMIN", "ROLE_ACCOUNTANT", "ROLE_EMPLOYEE")

                        /* 7. COMMON LOOKUPS */
                        .requestMatchers(HttpMethod.GET, "/api/departments/**", "/api/designations/**", "/api/employees/**", "/api/users/**", "/api/payment-methods/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/employees/change-password/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/employees/email-preference/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/employees/*/upload-photo").authenticated()
                        /* 8. GLOBAL FALLBACKS */
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyAuthority("ADMIN", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyAuthority("ADMIN", "ROLE_ADMIN", "ACCOUNTANT", "ROLE_ACCOUNTANT")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyAuthority("ADMIN", "ROLE_ADMIN", "ACCOUNTANT", "ROLE_ACCOUNTANT")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    // Enhanced Debugging
                    System.err.println("SECURITY REJECTION: URI=" + request.getRequestURI() + " | Method=" + request.getMethod() + " | Reason=" + authException.getMessage());
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Token invalid or missing.");
                }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000", "http://10.0.2.2:8080"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}