/**
 * auth 모듈. 강사 로그인(카카오)과 로그인 세션을 맡는다. 강사 가입은 InstructorFacade를 불러 처리한다.
 * 다른 모듈은 이 모듈을 부르지 않고, 필터가 남긴 로그인 강사 id를 {@code @AuthenticationPrincipal}로 받는다.
 */
package com.fitlink.auth;
