package com.fitlink;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** 모듈 사이 순환 의존이나, 다른 모듈의 공개하지 않은 패키지에 접근하는 코드가 생기면 실패한다. */
class ModularityTests {

	static final ApplicationModules modules = ApplicationModules.of(FitlinkApplication.class);

	@Test
	void 모듈_경계를_지킨다() {
		modules.verify();
	}

}
