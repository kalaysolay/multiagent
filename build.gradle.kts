buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:10.10.0")
        classpath("org.postgresql:postgresql:42.7.3")
    }
}

plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.flywaydb.flyway") version "10.10.0"
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
    
    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    
    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.5")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.5")
    
    // BCrypt для хеширования паролей
    implementation("org.springframework.security:spring-security-crypto")

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    annotationProcessor ("org.projectlombok:lombok")
    testCompileOnly ("org.projectlombok:lombok")
    testAnnotationProcessor ("org.projectlombok:lombok")

}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Flyway configuration for repair task
flyway {
    url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/iconix_agent_db"
    user = System.getenv("DB_USER") ?: "postgres"
    password = System.getenv("DB_PASSWORD") ?: "123qweasd"
    locations = arrayOf("classpath:db/migration")
}

// Явно указываем главный класс приложения
springBoot {
    mainClass.set("com.example.portal.Application")
}
