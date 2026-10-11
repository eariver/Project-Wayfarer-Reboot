plugins {
    application
}

// The test client compiles with the JDK 21 toolchain: javac 25 hits a compiler
// crash (Lower$FreeVarCollector ClassCastException) on this source; 21 compiles cleanly.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("io.github.eariver.wayfarer.tools.testclient.TestClient")
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
}

dependencies {
    implementation("org.geysermc.mcprotocollib:protocol:26.2-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("net.kyori:adventure-text-serializer-plain:4.23.0")
}
