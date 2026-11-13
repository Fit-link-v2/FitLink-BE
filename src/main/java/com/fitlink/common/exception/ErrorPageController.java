package com.fitlink.common.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 컨트롤러 밖(필터)에서 처리하지 못한 예외는 서블릿 컨테이너가 /error로 다시 보낸다. Spring Boot 기본 응답
// { timestamp, status, error, path } 대신 공통 처리기와 같은 모양으로 응답한다. 예외 로그는 컨테이너가 이미 남긴다
@RestController
class ErrorPageController implements ErrorController {

	@RequestMapping("/error")
	ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
		Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		HttpStatusCode status = HttpStatusCode.valueOf((code instanceof Integer value) ? value : 500);
		return ResponseEntity.status(status).body(ErrorResponse.forStatus(status));
	}

}
