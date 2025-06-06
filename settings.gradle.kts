pluginManagement {
    repositories {
        if (providers.environmentVariable("CI").getOrNull() == null) {
            mavenLocal()
        }
        gradlePluginPortal()
    }
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention").version(
            providers.gradleProperty("foojayToolchainPluginVersion").get()
        )
    }
    includeBuild("internal-convention-plugin")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "imp-server"

val isCi = providers.environmentVariable("CI").getOrNull()?.let { it != "false" } ?: false

buildCache {
    local {
        isEnabled = !isCi
        isPush = !isCi
    }
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("internal-convention-plugin")

include(
    listOf(
        ":common-test",
        ":lib:core",
        ":lib:bridge-model",
        ":lib:netty-bridge",
        ":lib:apache-bridge",
        ":lib:jdk-bridge",
        ":lib:shared",
        ":testing:spring",
        ":testing:stress",
        ":testing:performance",
        ":testing:jayway-jsonpath-not-available",
    )
)

