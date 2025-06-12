plugins {
    java
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.testKonvence)
}

dependencies {
    compileOnly(libs.jaywayJsonPath)
    compileOnly( libs.slf4jApi)
    testImplementation(libs.jaywayJsonPath)
    testImplementation(libs.selfie)
}
