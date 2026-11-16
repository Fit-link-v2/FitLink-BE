package com.fitlink.membership.model;

// 발급 응답에만 싣는 회원 링크 주소. 토큰 원본이 들어 있어 문자열로 바꿔도 주소가 나오지 않게 한다
public record IssuedLink(String url) {

	@Override
	public String toString() {
		return "IssuedLink[url=***]";
	}

}
