plugins {
    id("java-library")
    id("io.spring.dependency-management") version "1.1.7"
    id("jacoco")
    id("org.unbroken-dome.test-sets") version "4.1.0"
    id("maven-publish")
}

group = project.findProperty("group") as String? ?: "ai.qodo.command"
version = project.findProperty("version") as String? ?: "2.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

ext {
    set("springAiVersion", "1.0.1")
}

dependencies {
    // Spring Core (no Boot starter)
    api(platform("org.springframework.boot:spring-boot-dependencies:3.5.5"))
    api("org.springframework:spring-context")
    api("org.springframework:spring-web")
    api("org.springframework:spring-webmvc")
    api("org.springframework:spring-webflux")
    api("org.springframework.integration:spring-integration-core")

    // MCP SDK
    api(platform("io.modelcontextprotocol.sdk:mcp-bom:0.12.0"))
    api("io.modelcontextprotocol.sdk:mcp")
    api("io.modelcontextprotocol.sdk:mcp-spring-webflux")

    // Spring AI
    api(platform("org.springframework.ai:spring-ai-bom:1.0.1"))
    api("org.springframework.ai:spring-ai-starter-mcp-client")

    // Jackson for JSON/YAML
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-toml")

    // HTTP Client
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // Optional dependencies (provided by app module)
    compileOnly("org.springframework.boot:spring-boot-starter-actuator")
    compileOnly("io.micrometer:micrometer-core")

    // Micrometer Prometheus registry for metrics export
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Caffeine cache for bounded metric storage
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // ActiveMQ and JMS dependencies
    api("org.springframework.boot:spring-boot-starter-activemq")
    api("jakarta.jms:jakarta.jms-api")

    // Jakarta Servlet API
    api("jakarta.servlet:jakarta.servlet-api")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-launcher")
}

// Publishing configuration
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = "ai.qodo.command"
            artifactId = "internal-core"
            version = project.version.toString()

            pom {
                name.set("Qodo Command SDK Internal Core")
                description.set("Core framework for Qodo Command SDK - Spring Boot based AI agent orchestration")
                url.set("https://github.com/David-Parry/spring-command-sdk")

                licenses {
                    license {
                        name.set("GNU Affero General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.html")
                    }
                }

                developers {
                    developer {
                        id.set("davidparry")
                        name.set("David Parry")
                        email.set("d.dry.parry@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/David-Parry/spring-command-sdk.git")
                    developerConnection.set("scm:git:ssh://github.com/David-Parry/spring-command-sdk.git")
                    url.set("https://github.com/David-Parry/spring-command-sdk")
                }
            }
        }
    }

    repositories {
        mavenLocal()

        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/David-Parry/spring-command-sdk")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String? ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as String? ?: ""
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/test/html"))
        csv.required.set(false)
    }

    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(files("build/classes/java/main"))
    executionData.setFrom(fileTree(layout.buildDirectory).include("jacoco/test.exec"))
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.0".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

testSets {
    create("integrationTest")
}

tasks.named<Test>("integrationTest") {
    useJUnitPlatform()
}