package com.fitlink.schedule.api;

import java.util.List;

// 목록 응답은 { "items": [...] } 모양이다
record Items<T>(List<T> items) {

	static <T> Items<T> of(List<T> items) {
		return new Items<>(items);
	}

}
