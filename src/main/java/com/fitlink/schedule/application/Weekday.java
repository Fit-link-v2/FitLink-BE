package com.fitlink.schedule.application;

// API의 요일 이름. DB와 PostgreSQL의 extract(dow)는 0 = 일요일이라 변환은 이 enum 한 곳에서만 한다
public enum Weekday {

	MON, TUE, WED, THU, FRI, SAT, SUN;

	public int dow() {
		return (ordinal() + 1) % 7;
	}

	public static Weekday ofDow(int dow) {
		return values()[(dow + 6) % 7];
	}

}
