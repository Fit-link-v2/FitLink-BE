package com.fitlink.entitlement.model;

// 월 정액의 주기 방식. WEEK는 주당 N회(주기 7일), FOUR_WEEKS는 4주마다 N회(주기 28일)다
public enum PeriodUnit {

	WEEK(7), FOUR_WEEKS(28);

	private final int days;

	PeriodUnit(int days) {
		this.days = days;
	}

	public int days() {
		return days;
	}

}
