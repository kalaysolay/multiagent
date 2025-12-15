plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    java
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter:1.0.0-M6")
    
    // JPA и PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.postgresql:postgresql")
    
    // Spring AI для векторных хранилищ (pgvector)
    implementation("org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter:1.0.0-M6")
    
    // Flyway для миграций
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    
    // PlantUML для рендеринга диаграмм
    implementation("net.sourceforge.plantuml:plantuml:1.2024.5")
    
    // Asciidoctor для рендеринга AsciiDoc в HTML
    implementation("org.asciidoctor:asciidoctorj:2.5.10")
    
    // JGit для работы с Git репозиториями
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r")

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    annotationProcessor ("org.projectlombok:lombok")
    testCompileOnly ("org.projectlombok:lombok")
    testAnnotationProcessor ("org.projectlombok:lombok")

}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Явно указываем главный класс приложения
springBoot {
    mainClass.set("com.example.portal.Application")
}
