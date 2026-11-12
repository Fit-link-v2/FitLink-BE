package com.fitlink.schedule.application;

// 슬롯의 지금 상태 때문에 시각 변경 · 휴강을 받을 수 없다. 422와 사유 코드로 바꾼다
public class SlotRejectedException extends RuntimeException {

	private final Reason reason;

	SlotRejectedException(Reason reason) {
		super(reason.message);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	public enum Reason {

		SLOT_CANCELED("휴강한 수업이다"),

		CLASS_STARTED("이미 시작한 수업이다"),

		SLOT_HAS_BOOKINGS("예약이 있는 수업이다");

		private final String message;

		Reason(String message) {
			this.message = message;
		}

	}

}
