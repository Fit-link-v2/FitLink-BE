package com.fitlink.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// 회원 추가 · 재발급을 한 번씩 지나간 뒤 출력된 로그 전체에 링크 토큰 원본이 없는지 본다.
// 장애를 보려고 로그 수준을 낮춘 상황도 막도록 우리 코드와 웹 · 보안 · DB 프레임워크 로그를 가장 낮은 TRACE로 돌린다
@ExtendWith(OutputCaptureExtension.class)
class MembershipLoggingTests extends IntegrationTest {

	static final String LINK_BASE = "http://localhost:5173/m/";

	static final List<String> TRACE_LOGGERS = List.of("com.fitlink", "org.springframework.web",
			"org.springframework.security", "org.springframework.jdbc");

	@Autowired
	LoggingSystem logging;

	@Autowired
	SessionRepository sessions;

	Cookie session;

	@BeforeEach
	void 로그를_TRACE로() {
		TRACE_LOGGERS.forEach(name -> logging.setLogLevel(name, LogLevel.TRACE));
		session = new Cookie("SESSION", sessions.issue(new MembershipFixtures(jdbc).instructor("1001")));
	}

	@AfterEach
	void 로그_수준_되돌리기() {
		TRACE_LOGGERS.forEach(name -> logging.setLogLevel(name, null));
	}

	@Test
	void 회원_추가_재발급_흐름의_로그에_링크_토큰이_없다(CapturedOutput output) throws Exception {
		String first = token(mockMvc.perform(post("/instructor/members").header("X-CSRF", "1")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"name\": \"김OO\" }")).andExpect(status().isCreated()), "$.member.id");
		long memberId = Long.parseLong(first.substring(0, first.indexOf('/')));
		String firstToken = first.substring(first.indexOf('/') + 1);
		String secondToken = token(mockMvc
			.perform(post("/instructor/members/{id}/link", memberId).header("X-CSRF", "1").cookie(session))
			.andExpect(status().isCreated()), null);
		mockMvc.perform(get("/instructor/members").cookie(session)).andExpect(status().isOk());

		assertThat(output.getAll()).contains(" TRACE ").doesNotContain(firstToken, secondToken);
	}

	// idPath가 있으면 "id/토큰", 없으면 토큰
	private static String token(ResultActions result, String idPath) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		String linkUrl = JsonPath.read(body, "$.linkUrl");
		String token = linkUrl.substring(LINK_BASE.length());
		return idPath == null ? token : JsonPath.read(body, idPath).toString() + "/" + token;
	}

}
