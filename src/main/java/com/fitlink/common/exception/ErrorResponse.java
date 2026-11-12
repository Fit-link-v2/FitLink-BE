package com.fitlink.common.exception;

// 모든 API가 같은 모양으로 쓰는 에러 응답 본문 { code, message }. 코드마다 필드를 더할 일이 생기면 그때 넓힌다
public record ErrorResponse(String code, String message) {
}
