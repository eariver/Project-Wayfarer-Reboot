plugins {
    id("com.gradleup.shadow") version "9.6.1"
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { exclude(group = "org.bukkit", module = "bukkit") }
    implementation(project(":libs:wayfarer-common"))
    implementation("com.h2database:h2:2.5.252")

    testImplementation(platform("org.junit:junit-bom:5.14.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    processResources {
        inputs.property("version", version)
        filesMatching("plugin.yml") {
            expand("version" to version)
        }
    }
    shadowJar {
        archiveClassifier.set("")
    }
    build { dependsOn(shadowJar) }
}
