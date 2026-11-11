package com.fitlink.schedule.application;

// 경로의 id가 없거나 다른 강사의 것이다. 남의 것인지 없는 것인지는 구분해 알려 주지 않는다
public class NotFoundException extends RuntimeException {

	public NotFoundException() {
		super("찾을 수 없다");
	}

}
