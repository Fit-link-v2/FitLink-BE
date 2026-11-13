package com.fitlink.common.exception;

import java.util.Optional;

// 모듈이 자기 테이블의 DB 제약 위반을 에러 코드로 바꾼다. 앞의 확인을 동시에 지나 제약에 걸린 요청을 4xx로 돌려주기 위해서다.
// 모르는 제약이면 비워 돌려준다. 어느 모듈도 바꾸지 못한 제약 위반은 코드 버그로 보고 500으로 둔다
public interface ConstraintErrors {

	Optional<FitlinkException> find(String constraint);

}
