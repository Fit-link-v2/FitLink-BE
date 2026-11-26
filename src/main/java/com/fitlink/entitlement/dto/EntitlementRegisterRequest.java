package com.fitlink.entitlement.dto;

import com.fitlink.common.exception.InvalidRequestException;
import com.fitlink.entitlement.model.PassRegistration;
import com.fitlink.entitlement.model.PeriodUnit;
import com.fitlink.entitlement.model.Registration;
import com.fitlink.entitlement.model.SubscriptionRegistration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

// 횟수권 · 월 정액 등록은 같은 주소를 쓰고 kind로 모양이 갈린다. JSON 트리로 받아 kind를 먼저 보고 그 모양의 필드만 읽는다.
// record로 받으면 1.5 같은 소수가 정수 필드에 잘려 들어가고, 모르는 kind를 어느 필드 문제로 알려 줄 수 없다.
// 처음 틀린 필드 하나를 400으로 알려 준다. 종료일이 오늘 이후인지는 오늘을 아는 서비스가 본다
public final class EntitlementRegisterRequest {

	private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	private EntitlementRegisterRequest() {
	}

	public static Registration from(JsonNode body) {
		if (body == null || !body.isObject()) {
			throw new InvalidRequestException("body", "JSON 객체를 보낸다");
		}
		JsonNode kind = body.get("kind");
		String value = (kind != null && kind.isString()) ? kind.asString() : "";
		return switch (value) {
			case "PASS" -> pass(body);
			case "SUBSCRIPTION" -> subscription(body);
			default -> throw new InvalidRequestException("kind", "PASS 또는 SUBSCRIPTION을 보낸다");
		};
	}

	// { kind: "PASS", startsOn, endsOn, maxCount }
	private static PassRegistration pass(JsonNode body) {
		LocalDate startsOn = date(body, "startsOn");
		LocalDate endsOn = date(body, "endsOn");
		int maxCount = integer(body.get("maxCount"), "maxCount", 1, 999, "횟수는 1 ~ 999다");
		if (startsOn.isAfter(endsOn)) {
			throw new InvalidRequestException("endsOn", "종료일은 시작일 이후다");
		}
		return new PassRegistration(startsOn, endsOn, maxCount);
	}

	// { kind: "SUBSCRIPTION", limit: { per, count }, startsOn, weeks }. 종료일 · 주기 수는 받지 않는다
	private static SubscriptionRegistration subscription(JsonNode body) {
		JsonNode limit = body.get("limit");
		if (limit == null || !limit.isObject()) {
			throw new InvalidRequestException("limit", "{ per, count }를 보낸다");
		}
		PeriodUnit per = periodUnit(limit.get("per"));
		int count = integer(limit.get("count"), "limit.count", 1, 99, "횟수는 1 ~ 99다");
		LocalDate startsOn = date(body, "startsOn");
		int weeks = integer(body.get("weeks"), "weeks", 4, 48, "등록 기간은 4 ~ 48주, 4주 단위다");
		if (weeks % 4 != 0) {
			throw new InvalidRequestException("weeks", "등록 기간은 4 ~ 48주, 4주 단위다");
		}
		return new SubscriptionRegistration(per, count, startsOn, weeks);
	}

	private static PeriodUnit periodUnit(JsonNode value) {
		String name = (value != null && value.isString()) ? value.asString() : "";
		return Arrays.stream(PeriodUnit.values())
			.filter(unit -> unit.name().equals(name))
			.findFirst()
			.orElseThrow(() -> new InvalidRequestException("limit.per", "WEEK 또는 FOUR_WEEKS를 보낸다"));
	}

	// 정수만 받는다. 소수 · 문자열 · int 범위 밖은 범위 밖과 같은 문구다
	private static int integer(JsonNode value, String field, int min, int max, String reason) {
		if (value == null || !value.isInt() || value.asInt() < min || value.asInt() > max) {
			throw new InvalidRequestException(field, reason);
		}
		return value.asInt();
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
