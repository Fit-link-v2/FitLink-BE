package com.fitlink.membership.dto;

import com.fitlink.common.exception.InvalidRequestException;
import com.fitlink.membership.model.MemberChange;
import com.fitlink.membership.model.MemberStatus;
import java.util.Arrays;
import tools.jackson.databind.JsonNode;

// 보낸 필드만 바꾼다. "필드를 보내지 않음"(그대로)과 "null을 보냄"(메모 지우기)을 나눠야 해서 JSON 트리로 받는다.
// record의 Optional 필드는 두 경우 모두 Optional.empty()가 되어 구분하지 못한다.
// 이름 · 메모의 정리와 길이는 회원 추가(MemberAddRequest)와 같다
public final class MemberChangeRequest {

	private MemberChangeRequest() {
	}

	public static MemberChange from(JsonNode body) {
		if (body == null || !body.isObject()) {
			throw new InvalidRequestException("body", "JSON 객체를 보낸다");
		}
		return new MemberChange(name(body), body.has("memo"), memo(body), status(body));
	}

	// 없으면 null(그대로). 지울 수 없으므로 null도 400
	private static String name(JsonNode body) {
		if (!body.has("name")) {
			return null;
		}
		String name = text(body, "name", "이름은 1 ~ 50자다").strip();
		if (name.isEmpty() || name.length() > 50) {
			throw new InvalidRequestException("name", "이름은 1 ~ 50자다");
		}
		return name;
	}

	// null · ""이면 지운다
	private static String memo(JsonNode body) {
		if (!body.has("memo") || body.get("memo").isNull()) {
			return null;
		}
		String memo = text(body, "memo", "메모는 500자 이하다");
		if (memo.length() > 500) {
			throw new InvalidRequestException("memo", "메모는 500자 이하다");
		}
		return memo.isEmpty() ? null : memo;
	}

	private static MemberStatus status(JsonNode body) {
		if (!body.has("status")) {
			return null;
		}
		String status = text(body, "status", "ACTIVE 또는 ENDED를 보낸다");
		return Arrays.stream(MemberStatus.values())
			.filter(value -> value.name().equals(status))
			.findFirst()
			.orElseThrow(() -> new InvalidRequestException("status", "ACTIVE 또는 ENDED를 보낸다"));
	}

	private static String text(JsonNode body, String field, String reason) {
		JsonNode value = body.get(field);
		if (!value.isString()) {
			throw new InvalidRequestException(field, reason);
		}
		return value.asString();
	}

}
