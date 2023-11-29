import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "3.1.5"
	id("io.spring.dependency-management") version "1.1.3"
	kotlin("jvm") version "1.8.22"
	kotlin("plugin.spring") version "1.8.22"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
	implementation("org.springframework.cloud:spring-cloud-starter-aws:2.0.1.RELEASE")
	implementation("javax.xml.bind:jaxb-api:2.3.1")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("aws.smithy.kotlin:http-client-engine-okhttp:0.28.0")
	implementation("aws.smithy.kotlin:http-client-engine-crt:0.28.0")
	implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.11")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("aws.sdk.kotlin:ec2:0.33.1-beta")
	implementation("aws.sdk.kotlin:elasticbeanstalk:0.33.1-beta")
	implementation("aws.sdk.kotlin:secretsmanager:0.33.1-beta")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.projectreactor:reactor-test")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs += "-Xjsr305=strict"
		jvmTarget = "17"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.bootBuildImage {
	builder.set("paketobuildpacks/builder-jammy-base:latest")
}
