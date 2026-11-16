package com.fitlink.membership.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code fitlink.membership.*} 설정. 회원 링크는 {@code {linkBaseUrl}/m/{token}}이다. 도메인을 정하면 이 값만 바꾼다.
 */
@Validated
@ConfigurationProperties("fitlink.membership")
public record MembershipProperties(
		@NotBlank @Pattern(regexp = ".*[^/]", message = "끝에 /를 붙이지 않는다") String linkBaseUrl) {
}
