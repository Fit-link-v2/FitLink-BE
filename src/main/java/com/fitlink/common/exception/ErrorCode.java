package com.fitlink.common.exception;

import org.springframework.http.HttpStatus;

// 에러 응답의 code · message와 HTTP 상태를 정한다. 모듈마다 enum으로 구현하고, 공통 처리기는 이 세 값만 꺼내 쓴다
public interface ErrorCode {

	// enum이면 따로 구현하지 않아도 있다(상수 이름)
	String name();

	// 응답 code는 상수 이름이다. 상수 이름을 바꾸면 API 응답이 바뀐다
	default String code() {
		return name();
	}

	HttpStatus status();

	String message();

}
