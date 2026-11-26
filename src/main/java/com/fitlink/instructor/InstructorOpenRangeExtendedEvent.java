package com.fitlink.instructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 강사가 오픈 범위를 늘렸다. 설정을 바꾼 트랜잭션 안에서 동기로 발행하므로, 받는 쪽이 예외를 밖으로 던지면 설정 변경도
 * 되돌아간다. 실패를 스스로 처리하고 예외를 던지지 않는 받는 쪽의 실패는 설정 변경을 되돌리지 않는다.
 * 받는 쪽이 시계를 다시 읽지 않도록 요청에서 구한 오늘(서울)과 지금을 싣는다. 자정 경계에서 리스너끼리 날짜가 어긋나지 않게 하려는 것이다
 */
public record InstructorOpenRangeExtendedEvent(long instructorId, LocalDate today, OffsetDateTime now) {
}
