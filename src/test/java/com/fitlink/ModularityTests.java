package com.fitlink;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** 모듈 사이 순환 · 허용하지 않은 패키지 접근이 생기면 실패한다(BE-ADR-0011). */
class ModularityTests {

	static final ApplicationModules modules = ApplicationModules.of(FitlinkApplication.class);

	@Test
	void 모듈_경계를_지킨다() {
		modules.verify();
	}

}
