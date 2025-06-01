plugins {
    java
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.testKonvence)
}

dependencies {
    compileOnly(libs.jaywayJsonPath)
    testImplementation(libs.jaywayJsonPath)
    testImplementation(libs.selfie)
}
