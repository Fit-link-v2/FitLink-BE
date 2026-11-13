package com.fitlink.auth.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;

// 처리되지 않은 예외는 서블릿 컨테이너가 /error로 다시 보낸다. 이때는 세션 필터가 돌지 않아 인증 정보가 없으므로,
// /error까지 인증을 요구하면 원래 500이 401로 바뀌어 보인다. 응답 본문도 공통 처리기와 같은 모양이다
class ErrorDispatchTests extends IntegrationTest {

	@Test
	void 에러_디스패치는_인증_없이_원래_상태_코드로_응답한다() throws Exception {
		mockMvc.perform(get("/error").with(request -> {
			request.setDispatcherType(DispatcherType.ERROR);
			request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
			request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/instructor/recurrences");
			return request;
		})).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
	}

}
