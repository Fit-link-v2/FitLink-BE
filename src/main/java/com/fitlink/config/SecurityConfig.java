package com.fitlink.config;

import com.fitlink.instructor.api.SessionAuthenticationFilter;
import com.fitlink.instructor.application.SessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * 로그인 · 세션은 직접 구현한다(BE-ADR-0008 · BE-ADR-0014). Spring Security의 폼 로그인 · HTTP Basic ·
 * HttpSession · 기본 CSRF 토큰은 끄고, 인증되지 않은 요청에는 401 {@code UNAUTHENTICATED}를 돌려준다.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SessionService sessions, ErrorResponseWriter errors) throws Exception {
		return http
			.csrf(csrf -> csrf.disable())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.logout(logout -> logout.disable())
			.requestCache(cache -> cache.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilterBefore(new SessionAuthenticationFilter(sessions), AnonymousAuthenticationFilter.class)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> errors.write(response,
					HttpStatus.UNAUTHORIZED, new ErrorResponse("UNAUTHENTICATED", "로그인이 필요합니다."))))
			.build();
	}

}
