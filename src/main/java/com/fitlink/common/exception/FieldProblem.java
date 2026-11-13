package com.fitlink.common.exception;

// 400 VALIDATION_FAILED 응답의 fields 원소. 어느 필드가 왜 틀렸는지
public record FieldProblem(String name, String reason) {
}
