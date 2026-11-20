package com.fitlink.membership.dto;

// linkUrl에 토큰 원본이 있다. Spring MVC는 로그 수준을 낮추면 응답 본문을 toString으로 찍으므로 주소를 가린다
public record LinkIssuedResponse(String linkUrl) {

	@Override
	public String toString() {
		return "LinkIssuedResponse[linkUrl=***]";
	}

}
