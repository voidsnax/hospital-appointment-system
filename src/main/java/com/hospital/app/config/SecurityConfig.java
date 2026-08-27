package com.hospital.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                // Public pages — no login needed
                .requestMatchers("/", "/login", "/register", "/verify-otp", "/resend-otp",
                        "/forgot-password", "/reset-password",
                        "/css/**", "/js/**", "/img/**", "/favicon.svg", "/error")
                .permitAll()
                // Role-protected areas
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/doctor/**").hasRole("DOCTOR")
                .requestMatchers("/patient/**").hasRole("PATIENT")
                // Everything else: any logged-in user
                .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login") // our custom login page (GET)
                        .loginProcessingUrl("/login") // where the form POSTs
                        .usernameParameter("email") // form field name for username
                        .passwordParameter("password") // form field name for password
                        .successHandler(roleBasedRedirect()) // redirect after login
                        .failureHandler(rememberEmailOnFailure()) // wrong credentials
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());

        return http.build();
    }

    // After successful login, send each role to its own dashboard
    private AuthenticationSuccessHandler roleBasedRedirect() {
        return (request, response, authentication) -> {
            request.getSession().removeAttribute("lastLoginEmail");
            String target = authentication.getAuthorities().stream()
                    .map(auth -> auth.getAuthority())
                    .filter(auth -> auth.startsWith("ROLE_"))
                    .findFirst()
                    .map(auth -> switch (auth) {
                        case "ROLE_ADMIN" -> "/admin/dashboard";
                        case "ROLE_DOCTOR" -> "/doctor/dashboard";
                        case "ROLE_PATIENT" -> "/patient/dashboard";
                        default -> "/login?error";
                    })
                    .orElse("/login?error");

            response.sendRedirect(target);
        };
    }

    // On login failure: remember the typed email so the login form can re-fill it
    private AuthenticationFailureHandler rememberEmailOnFailure() {
        return (request, response, exception) -> {
            String email = request.getParameter("email");
            if (email != null && !email.isBlank()) {
                request.getSession(true).setAttribute("lastLoginEmail", email);
            }
            response.sendRedirect("/login?error");
        };
    }
}