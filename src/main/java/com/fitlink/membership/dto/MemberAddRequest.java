package com.fitlink.membership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 이름은 앞뒤 공백을 지운 뒤 검사하고 저장한다. 빈 메모는 메모가 없는 것과 같다.
// 길이는 Java 문자(UTF-16) 수로 센다. DB CHECK는 코드 포인트 수라, 이모지가 섞이면 여기가 더 엄격하다
public record MemberAddRequest(
		@NotNull(message = "이름을 보낸다") @Size(min = 1, max = 50, message = "이름은 1 ~ 50자다") String name,
		@Size(max = 500, message = "메모는 500자 이하다") String memo) {

	public MemberAddRequest {
		name = name == null ? null : name.strip();
		memo = memo == null || memo.isEmpty() ? null : memo;
	}

}
