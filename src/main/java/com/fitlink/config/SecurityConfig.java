package com.fitlink.config;

import com.fitlink.instructor.api.SessionAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * 로그인 · 세션은 카카오 로그인과 DB 세션 테이블로 직접 구현한다. Spring Security의 폼 로그인 · HTTP Basic ·
 * HttpSession · 기본 CSRF 토큰은 끄고, 인증되지 않은 요청에는 401 {@code UNAUTHENTICATED}를 돌려준다.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SessionAuthenticationFilter sessionFilter,
			ErrorResponseWriter errors) throws Exception {
		return http
			.csrf(csrf -> csrf.disable())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.logout(logout -> logout.disable())
			.requestCache(cache -> cache.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
			.addFilterAfter(new CsrfHeaderFilter(errors), AuthorizationFilter.class)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health").permitAll()
				.requestMatchers(HttpMethod.POST, "/auth/logout").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> errors.write(response,
					HttpStatus.UNAUTHORIZED, new ErrorResponse("UNAUTHENTICATED", "로그인이 필요합니다."))))
			.build();
	}

	// Filter Bean은 Spring Boot가 서블릿 필터로도 자동 등록한다. Security 체인 안에서만 돌도록 자동 등록을 끈다
	@Bean
	FilterRegistrationBean<SessionAuthenticationFilter> sessionFilterRegistration(SessionAuthenticationFilter filter) {
		FilterRegistrationBean<SessionAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

}
