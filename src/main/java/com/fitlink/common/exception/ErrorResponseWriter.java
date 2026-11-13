package com.fitlink.common.exception;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 필터 단계의 에러는 컨트롤러를 거치지 않아 공통 처리기가 받지 못하므로 응답 본문을 직접 쓴다
@Component
public class ErrorResponseWriter {

	private final JsonMapper json;

	ErrorResponseWriter(JsonMapper json) {
		this.json = json;
	}

	public void write(HttpServletResponse response, ErrorCode code) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		json.writeValue(response.getOutputStream(), ErrorResponse.of(code));
	}

}
