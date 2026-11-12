/**
 * common 모듈. 여러 모듈이 같이 쓰고 도메인 규칙이 없는 코드(시계 설정, 에러 응답 모양)를 둔다.
 * 하위 폴더까지 다른 모듈에 열고, 이 모듈은 다른 모듈을 부르지 않는다.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.fitlink.common;

import org.springframework.modulith.ApplicationModule;
