package com.fitlink;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

// 스케줄 실행은 기본으로 켜져 있고, 설정으로 끌 수 있다. 통합 테스트는 끈 상태로 돈다
class SchedulingConfigTests {

	ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(FitlinkApplication.Scheduling.class);

	@Test
	void 설정이_없으면_스케줄_실행이_켜진다() {
		runner.run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
	}

	@Test
	void 꺼_두면_스케줄_실행이_없다() {
		runner.withPropertyValues("fitlink.scheduling.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
	}

}
