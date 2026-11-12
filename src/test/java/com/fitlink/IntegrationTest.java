package com.fitlink;

import com.fitlink.auth.application.KakaoOAuthClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// DB가 닿는 테스트의 공통 기반. 설정이 같아야 Spring 컨텍스트 · 컨테이너를 재사용하므로
// 클래스마다 설정을 더하지 않고 여기서만 바꾼다
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class })
@SpringBootTest(properties = { "KAKAO_CLIENT_ID=test-client-id", "KAKAO_CLIENT_SECRET=test-client-secret" })
@AutoConfigureMockMvc
public abstract class IntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcClient jdbc;

	// 실제 카카오를 부르지 않는다. 로그인 흐름 테스트가 돌려줄 사용자나 실패를 정한다
	@MockitoBean
	protected KakaoOAuthClient kakao;

	// 시간표 테스트가 "지금"을 정한다. 기본은 목요일 12:00(서울)
	@Autowired
	protected TestClock testClock;

	// 동시성 테스트는 @Transactional 롤백과 맞지 않아 매 테스트 전에 비운다.
	// 테이블 목록은 DB에서 읽는다. 마이그레이션에 테이블을 더하고 여기를 빠뜨려 데이터가 다음 테스트로 새지 않게 하려는 것이다
	@BeforeEach
	void truncateTables() {
		String tables = String.join(", ", jdbc.sql("""
				SELECT quote_ident(table_name) FROM information_schema.tables
				 WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'
				""").query(String.class).list());
		jdbc.sql("TRUNCATE " + tables + " RESTART IDENTITY CASCADE").update();
		testClock.reset();
	}

}
