package com.fitlink.config;

// 에러 응답 본문(prd 3.2 공통 규칙). 코드마다 필드를 더할 일이 생기면 그때 넓힌다
record ErrorResponse(String code, String message) {
}
