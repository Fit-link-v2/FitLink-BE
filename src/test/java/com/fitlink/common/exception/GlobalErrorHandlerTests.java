package com.fitlink.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 어느 모듈의 컨트롤러에서 났든 같은 모양으로 응답하는지, Spring MVC 예외가 원래 상태 코드를 지키는지,
// 4xx는 warn · 5xx는 error로 남는지 본다. DB · Security 없이 처리기와 테스트용 컨트롤러만 띄운다
@ExtendWith(OutputCaptureExtension.class)
class GlobalErrorHandlerTests {

	ConstraintErrors constraints = constraint -> switch (constraint) {
		case "known_uidx", "known_check" -> Optional.of(new FitlinkException(TestErrorCode.TAKEN));
		default -> Optional.empty();
	};

	MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
		.setControllerAdvice(new GlobalErrorHandler(List.of(constraints)))
		.build();

	@Test
	void FitlinkException은_에러_코드의_상태로_응답하고_details를_옆에_펼친다(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/fitlink"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TAKEN"))
			.andExpect(jsonPath("$.message").value("이미 있다"))
			.andExpect(jsonPath("$.ids[1]").value(2))
			.andExpect(jsonPath("$.details").doesNotExist());

		assertThat(output).contains("WARN").contains("[TAKEN]");
	}

	@Test
	void 본문_검증_실패는_400_VALIDATION_FAILED와_필드() throws Exception {
		mockMvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{\"count\": 0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[*].name", containsInAnyOrder("name", "count")));
	}

	@Test
	void JSON_형식_오류는_400과_body_필드() throws Exception {
		mockMvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value("body"));
	}

	@Test
	void 경로_값이_숫자가_아니면_400과_경로_이름() throws Exception {
		mockMvc.perform(get("/items/abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields[0].name").value("id"));
	}

	@Test
	void 경로_값_검사_실패도_400이고_warn으로_남는다(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/items/0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value("id"))
			.andExpect(jsonPath("$.fields[0].reason").value("1 이상이다"));

		assertThat(output).contains("WARN").contains("HandlerMethodValidationException").doesNotContain("ERROR");
	}

	@Test
	void 필수_헤더가_없으면_400이고_warn으로_남는다(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/header"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value("X-Need"));

		assertThat(output).contains("WARN").contains("MissingRequestHeaderException");
	}

	@Test
	void 필수_쿼리_파라미터가_없으면_400과_파라미터_이름() throws Exception {
		mockMvc.perform(get("/param"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields[0].name").value("size"));
	}

	@Test
	void 지원하지_않는_메서드는_405와_상태_이름_코드_Allow_헤더는_그대로() throws Exception {
		mockMvc.perform(post("/fitlink"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(header().string("Allow", containsString("GET")))
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
			.andExpect(jsonPath("$.message").isString());
	}

	@Test
	void 모듈이_아는_유니크_위반은_그_모듈의_에러_코드() throws Exception {
		mockMvc.perform(get("/violation/unique/known_uidx"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TAKEN"));
	}

	@Test
	void 모듈이_아는_CHECK_위반도_그_모듈의_에러_코드() throws Exception {
		mockMvc.perform(get("/violation/check/known_check"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TAKEN"));
	}

	// DB 언어 설정이 영어가 아니어도 따옴표 안의 제약 이름으로 찾는다
	@Test
	void 한국어_DB_메시지의_제약_위반도_그_모듈의_에러_코드() throws Exception {
		mockMvc.perform(get("/violation/korean/known_uidx"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TAKEN"));
	}

	@Test
	void 아무_모듈도_모르는_제약_위반은_500(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/violation/unique/unknown_uidx"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

		assertThat(output).contains("ERROR").contains("unknown_uidx");
	}

	@Test
	void 처리하지_못한_예외는_500이고_원인은_응답이_아니라_로그에만_남는다(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value("일시적인 오류가 났다"))
			.andExpect(content().string(not(containsString("내부 세부 정보"))));

		assertThat(output).contains("ERROR").contains("내부 세부 정보").contains("uri=/boom");
	}

	// Security 필터가 401 · 403으로 바꾸도록 처리기가 삼키지 않고 밖으로 내보낸다
	@Test
	void Security_예외는_처리하지_않고_다시_던진다() {
		assertThatThrownBy(() -> mockMvc.perform(get("/denied"))).hasRootCauseInstanceOf(AccessDeniedException.class);
	}

	@Test
	void details에_code_message_키는_쓸_수_없다() {
		assertThatThrownBy(() -> new FitlinkException(TestErrorCode.TAKEN, Map.of("message", "덮어쓰기")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	enum TestErrorCode implements ErrorCode {

		TAKEN;

		@Override
		public HttpStatus status() {
			return HttpStatus.CONFLICT;
		}

		@Override
		public String message() {
			return "이미 있다";
		}

	}

	record Body(@NotNull(message = "이름을 보낸다") String name, @Min(value = 1, message = "1 이상이다") int count) {
	}

	@RestController
	static class TestController {

		@GetMapping("/fitlink")
		void fitlink() {
			throw new FitlinkException(TestErrorCode.TAKEN, Map.of("ids", List.of(1, 2)));
		}

		@PostMapping("/body")
		void body(@Valid @RequestBody Body body) {
		}

		@GetMapping("/items/{id}")
		void item(@PathVariable @Min(value = 1, message = "1 이상이다") long id) {
		}

		@GetMapping("/header")
		void header(@RequestHeader("X-Need") String need) {
		}

		@GetMapping("/violation/unique/{constraint}")
		void unique(@PathVariable String constraint) {
			throw new DuplicateKeyException(
					"ERROR: duplicate key value violates unique constraint \"" + constraint + "\"");
		}

		@GetMapping("/violation/korean/{constraint}")
		void korean(@PathVariable String constraint) {
			throw new DuplicateKeyException("오류: 중복된 키 값이 \"" + constraint + "\" 고유 제약 조건을 위반함");
		}

		@GetMapping("/violation/check/{constraint}")
		void check(@PathVariable String constraint) {
			throw new DataIntegrityViolationException(
					"ERROR: new row for relation \"t\" violates check constraint \"" + constraint + "\"");
		}

		@GetMapping("/param")
		void param(@RequestParam int size) {
		}

		@GetMapping("/denied")
		void denied() {
			throw new AccessDeniedException("권한 없음");
		}

		@GetMapping("/boom")
		void boom() {
			throw new IllegalStateException("내부 세부 정보");
		}

	}

}
