package com.fitlink;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 모듈 안의 호출 방향과 모듈 루트의 이름 규칙을 검사한다. 모듈 사이 경계는 {@link ModularityTests}가 본다.
 * 폴더를 역할별로 나누면 package-private으로 숨길 수 없게 되어, 그 보호를 이 테스트가 대신한다.
 */
class ArchitectureTests {

	static final JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("com.fitlink");

	static final List<String> ROOT_SUFFIXES = List.of("Facade", "Command", "Info");

	@Test
	void 모듈_안은_controller에서_service_repository_방향으로만_부른다() {
		layeredArchitecture().consideringOnlyDependenciesInLayers()
			.layer("facade").definedBy("com.fitlink.*")
			.layer("controller").definedBy("com.fitlink.*.controller..")
			.layer("dto").definedBy("com.fitlink.*.dto..")
			.layer("security").definedBy("com.fitlink.*.security..")
			.layer("service").definedBy("com.fitlink.*.service..")
			.layer("client").definedBy("com.fitlink.*.client..")
			.layer("repository").definedBy("com.fitlink.*.repository..")
			.layer("model").definedBy("com.fitlink.*.model..")
			.layer("exception").definedBy("com.fitlink.*.exception..")
			.layer("config").definedBy("com.fitlink.*.config..")
			.whereLayer("controller").mayNotBeAccessedByAnyLayer()
			.whereLayer("dto").mayOnlyBeAccessedByLayers("controller")
			.whereLayer("facade").mayOnlyBeAccessedByLayers("service")
			.whereLayer("service").mayOnlyBeAccessedByLayers("controller", "security", "facade")
			.whereLayer("client").mayOnlyBeAccessedByLayers("service")
			.whereLayer("repository").mayOnlyBeAccessedByLayers("service")
			.check(classes);
	}

	@Test
	void common은_다른_모듈을_모른다() {
		noClasses().that()
			.resideInAPackage("com.fitlink.common..")
			.should()
			.dependOnClassesThat(resideInAPackage("com.fitlink..").and(not(resideInAPackage("com.fitlink.common.."))))
			.check(classes);
	}

	@Test
	void 모듈_루트에는_모듈_이름으로_시작하는_Facade_Command_Info만_둔다() {
		classes().that()
			.resideInAPackage("com.fitlink.*")
			.and()
			.doNotHaveSimpleName("package-info")
			.and()
			.areTopLevelClasses()
			.should(beModuleEntryType())
			.allowEmptyShould(true)
			.check(classes);
	}

	// 레이어 규칙은 service가 Facade를 부르는 것을 허용한다(다른 모듈의 Facade). 같은 모듈의 Facade를 부르면
	// Facade → service → Facade 순환이 되므로 따로 막는다. 모듈 루트는 아래 순환 검사의 대상이 아니다
	@Test
	void 모듈_안에서는_자기_Facade를_부르지_않는다() {
		classes().that()
			.resideInAPackage("com.fitlink.*..")
			.should(notCallOwnFacade())
			.check(classes);
	}

	@Test
	void 모듈_안의_역할_폴더끼리_순환하지_않는다() {
		slices().matching("com.fitlink.(*).(*)..").should().beFreeOfCycles().check(classes);
	}

	private static ArchCondition<JavaClass> notCallOwnFacade() {
		return new ArchCondition<>("같은 모듈의 Facade에 의존하지 않는다") {
			@Override
			public void check(JavaClass type, ConditionEvents events) {
				String module = moduleOf(type.getPackageName());
				if (module == null || type.getSimpleName().endsWith("Facade")) {
					return;
				}
				type.getDirectDependenciesFromSelf()
					.stream()
					.map(dependency -> dependency.getTargetClass())
					.filter(target -> target.getSimpleName().endsWith("Facade")
							&& target.getPackageName().equals("com.fitlink." + module))
					.forEach(target -> events.add(SimpleConditionEvent.violated(type,
							type.getName() + "가 같은 모듈의 " + target.getSimpleName() + "를 부른다")));
			}
		};
	}

	// com.fitlink.{모듈}.{역할}... 이면 모듈 이름, 모듈 루트나 그 밖이면 null
	private static String moduleOf(String packageName) {
		String[] parts = packageName.split("\\.");
		return parts.length >= 4 && packageName.startsWith("com.fitlink.") ? parts[2] : null;
	}

	private static ArchCondition<JavaClass> beModuleEntryType() {
		return new ArchCondition<>("모듈 이름으로 시작하고 Facade · Command · Info로 끝난다") {
			@Override
			public void check(JavaClass type, ConditionEvents events) {
				String module = type.getPackageName().substring(type.getPackageName().lastIndexOf('.') + 1);
				String prefix = Character.toUpperCase(module.charAt(0)) + module.substring(1);
				String name = type.getSimpleName();
				boolean ok = name.startsWith(prefix) && ROOT_SUFFIXES.stream().anyMatch(name::endsWith);
				events.add(new SimpleConditionEvent(type, ok, type.getName() + "는 모듈 루트에 둘 수 없는 이름이다"));
			}
		};
	}

}
