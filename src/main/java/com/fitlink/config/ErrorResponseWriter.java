package com.fitlink.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 필터 단계의 에러는 컨트롤러를 거치지 않으므로 응답 본문을 직접 쓴다
@Component
class ErrorResponseWriter {

	private final JsonMapper json;

	ErrorResponseWriter(JsonMapper json) {
		this.json = json;
	}

	void write(HttpServletResponse response, HttpStatus status, ErrorResponse body) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		json.writeValue(response.getOutputStream(), body);
	}

}
