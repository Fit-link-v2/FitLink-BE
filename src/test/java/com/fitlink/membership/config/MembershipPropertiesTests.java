package com.fitlink.membership.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class MembershipPropertiesTests {

	final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	// 끝이 /이면 링크가 {주소}//m/{token}이 된다
	@Test
	void 회원_화면_주소는_슬래시로_끝나면_안_된다() {
		assertThat(validator.validate(new MembershipProperties("http://localhost:5173/"))).hasSize(1);
		assertThat(validator.validate(new MembershipProperties("http://localhost:5173"))).isEmpty();
	}

}
