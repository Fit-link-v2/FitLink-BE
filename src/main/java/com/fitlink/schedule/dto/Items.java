package com.fitlink.schedule.dto;

import java.util.List;

// 목록 응답은 { "items": [...] } 모양이다
public record Items<T>(List<T> items) {

	public static <T> Items<T> of(List<T> items) {
		return new Items<>(items);
	}

}
