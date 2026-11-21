package com.fitlink.entitlement.dto;

import com.fitlink.common.exception.InvalidRequestException;
import com.fitlink.entitlement.model.PassRegistration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

// 횟수권 · 월 정액 등록은 같은 주소를 쓰고 kind로 모양이 갈린다. JSON 트리로 받아 kind를 먼저 보고 그 모양의 필드만 읽는다.
// record로 받으면 1.5 같은 소수가 정수 필드에 잘려 들어가고, 모르는 kind를 어느 필드 문제로 알려 줄 수 없다.
// 처음 틀린 필드 하나를 400으로 알려 준다. 종료일이 오늘 이후인지는 오늘을 아는 서비스가 본다
public final class EntitlementRegisterRequest {

	private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	private EntitlementRegisterRequest() {
	}

	public static PassRegistration from(JsonNode body) {
		if (body == null || !body.isObject()) {
			throw new InvalidRequestException("body", "JSON 객체를 보낸다");
		}
		JsonNode kind = body.get("kind");
		if (kind == null || !kind.isString() || !kind.asString().equals("PASS")) {
			throw new InvalidRequestException("kind", "PASS를 보낸다");
		}
		return pass(body);
	}

	private static PassRegistration pass(JsonNode body) {
		LocalDate startsOn = date(body, "startsOn");
		LocalDate endsOn = date(body, "endsOn");
		JsonNode maxCount = body.get("maxCount");
		if (maxCount == null || !maxCount.isInt() || maxCount.asInt() < 1 || maxCount.asInt() > 999) {
			throw new InvalidRequestException("maxCount", "횟수는 1 ~ 999다");
		}
		if (startsOn.isAfter(endsOn)) {
			throw new InvalidRequestException("endsOn", "종료일은 시작일 이후다");
		}
		return new PassRegistration(startsOn, endsOn, maxCount.asInt());
	}

	// yyyy-MM-dd만 받는다. 부호 붙은 다섯 자리 이상 연도는 DB date 범위를 넘을 수 있어 형식에서 막는다
	private static LocalDate date(JsonNode body, String field) {
		JsonNode value = body.get(field);
		if (value == null || !value.isString() || !DATE.matcher(value.asString()).matches()) {
			throw new InvalidRequestException(field, "날짜를 yyyy-MM-dd로 보낸다");
		}
		try {
			return LocalDate.parse(value.asString());
		}
		catch (DateTimeParseException e) {
			throw new InvalidRequestException(field, "날짜를 yyyy-MM-dd로 보낸다");
		}
	}

}
