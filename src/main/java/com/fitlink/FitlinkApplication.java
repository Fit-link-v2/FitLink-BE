package com.fitlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// 아이디 · 비밀번호 로그인을 쓰지 않으므로 Spring Security 기본 계정(로그에 찍히는 임시 비밀번호)을 만들지 않는다
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FitlinkApplication {

	public static void main(String[] args) {
		SpringApplication.run(FitlinkApplication.class, args);
	}

}
