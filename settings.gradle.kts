pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "Project-Wayfarer-Reboot"

include(":libs:wayfarer-common")
include(":plugins:waymark-provider")
include(":plugins:wayfarer-main")
include(":plugins:wayfarer-frontier")
include(":tools:wayfarer-test-client")
