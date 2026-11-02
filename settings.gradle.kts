plugins {
	// 로컬에 toolchain 버전의 JDK가 없으면 내려받는다
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "fitlink-be"
