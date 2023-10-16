import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.MetaTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask

plugins {
    id("org.springframework.boot") version "3.1.4"
    id("io.spring.dependency-management") version "1.1.3"
    id("org.openapi.generator") version "6.6.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.3.0"
    kotlin("jvm") version "1.8.22"
    kotlin("plugin.spring") version "1.8.22"
    kotlin("plugin.jpa") version "1.8.22"
}

group = "com.woowahan"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.4.2")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.1.2")
}

ktlint {
    filter {
        exclude { it.file.absolutePath.contains("/generated/") }
    }
    filter {
        verbose.set(true)
        disabledRules.addAll("import-ordering")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val openApiPackageName = "openapi"

val customApiPackage = "$openApiPackageName.api"
val customInvokerPackage = "$openApiPackageName.invoker"
val customModelPackage = "$openApiPackageName.model"

val contractDir = "$rootDir/../docs/contract"
val openApiGenerateDir = "$buildDir/openapi"

val contractFileNames = fileTree(contractDir)
    .filter { it.extension == "yaml" }
    .map { it.name }

val generateOpenApiTasks = contractFileNames.map { createOpenApiGenerateTask(it) }

// OpenAPI CodeGen 코드 생성
tasks.register("createOpenApi") {
    doFirst {
        println("Creating Code By OpenAPI...")
    }
    doLast {
        println("OpenAPI Code created.")
    }
    dependsOn(generateOpenApiTasks)
}

// 빌드된 API 파일 이동
tasks.register("moveGeneratedSources") {
    doFirst {
        println("Moving generated sources...")
    }
    doLast {
        listOf(customApiPackage, customModelPackage, customInvokerPackage)
            .map { it.replace(".", "/") }
            .forEach { packagePath ->
                val originDir = file("$openApiGenerateDir/src/main/kotlin/$packagePath")
                val destinationDir = file("src/main/generated/$packagePath")
                originDir.listFiles { file -> file.extension == "kt" }?.forEach { file ->
                    val resolvedFile = destinationDir.resolve(file.name)
                    if (!resolvedFile.exists() && file.name != "Application.kt") {
                        file.copyTo(destinationDir.resolve(file.name), true)
                    }
                }
            }
        println("Generated sources moved.")
    }
    dependsOn(tasks.getByName("createOpenApi"))
}

// 생성된 OpenAPI 디렉터리 삭제
tasks.register("cleanGeneratedDirectory") {
    doFirst {
        println("Cleaning generated directory...")
    }
    doLast {
        val generatedDir = file(openApiGenerateDir)
        generatedDir.deleteRecursively()
        println("Generated directory cleaned.")
    }
    dependsOn(tasks.getByName("moveGeneratedSources"))
}

// 생성된 OpenAPI 코드에 대한 참조 경로 설정
sourceSets {
    main {
        kotlin {
            srcDirs("src/main/generated")
        }
    }
}

// clean 시 generated 디렉터리 삭제
tasks.named("clean") {
    val generatedDir = file("src/main/generated")
    generatedDir.deleteRecursively()
    println("Generated directory cleaned.")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
    dependsOn(tasks.getByName("cleanGeneratedDirectory"))
}

fun createOpenApiGenerateTask(fileName: String): TaskProvider<GenerateTask> {
    val taskName = "openApiGenerate_$fileName"

    return tasks.register(taskName, GenerateTask::class) {
        generatorName.set("kotlin-spring")
        inputSpec.set("$contractDir/$fileName")
        outputDir.set(openApiGenerateDir)
        apiPackage.set(customApiPackage)
        invokerPackage.set(customInvokerPackage)
        modelPackage.set(customModelPackage)
        configOptions.set(
            mapOf(
                "dateLibrary" to "kotlin-spring",
                "useSpringBoot3" to "true",
                "useTags" to "true",
                "interfaceOnly" to "true"
            )
        )
        // 템플릿 디렉터리 설정
        templateDir.set("$contractDir/template")
    }
}

// OpenAPI CodeGen 코드 검증
// 사용 시 Task 등록 필요
fun createOpenApiValidateTask(fileName: String): TaskProvider<ValidateTask> {
    val taskName = "openApiValidate_$fileName"

    return tasks.register(taskName, ValidateTask::class) {
        inputSpec.set("$contractDir/$fileName")
        recommend.set(true)
    }
}

// OpenAPI CodeGen 메타 정보 생성
// 사용 시 Task 등록 필요
fun createOpenApiMetaTask(fileName: String): TaskProvider<MetaTask> {
    val taskName = "openApiMeta_$fileName"

    return tasks.register(taskName, MetaTask::class) {
        generatorName.set("meta")
        packageName.set(openApiPackageName)
        outputFolder.set("$buildDir/meta")
    }
}
