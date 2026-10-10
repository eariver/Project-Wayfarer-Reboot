plugins {
    java
    `maven-publish`
}

// Java 21 target: sources compile for JVM 21 and run unchanged on the production
// Java 25 runtimes (Lobby/Main Java 25, Frontier Java 25, Paper 1.21.11 API).
val javaVersion = JavaVersion.VERSION_21

allprojects {
    group = "io.github.eariver.wayfarer"
    version = "0.1.0-alpha"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") // paper-api
        maven("https://jitpack.io")                               // VaultAPI
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
        }
    }
}
