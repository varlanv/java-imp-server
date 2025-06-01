plugins {
    `java-library`
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.testKonvence)
}

dependencies {
    api(projects.lib.shared)
    compileOnly(libs.jaywayJsonPath)
    testImplementation(libs.jaywayJsonPath)
    testImplementation(libs.selfie)
}
