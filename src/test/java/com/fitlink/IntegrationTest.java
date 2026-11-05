package com.fitlink;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

// DB가 닿는 테스트의 공통 기반. 설정이 같아야 Spring 컨텍스트 · 컨테이너를 재사용하므로
// 클래스마다 설정을 더하지 않고 여기서만 바꾼다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "KAKAO_CLIENT_ID=test-client-id")
@AutoConfigureMockMvc
public abstract class IntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcClient jdbc;

	// 동시성 테스트는 @Transactional 롤백과 맞지 않아 매 테스트 전에 비운다
	@BeforeEach
	void truncateTables() {
		jdbc.sql("TRUNCATE instructor, setting, instructor_session RESTART IDENTITY CASCADE").update();
	}

}
