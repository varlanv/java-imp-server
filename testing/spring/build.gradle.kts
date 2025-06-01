plugins {
    `java-library`
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.testKonvence)
    id("org.springframework.boot") version "3.5.0"
}

internalConvention {
    internalModule = true
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(projects.lib.core)
}
