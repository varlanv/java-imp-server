plugins {
    java
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.testKonvence)
}

internalConvention {
    addSlf4jApiDependency = true
}

dependencies {
    compileOnly(libs.jaywayJsonPath)
    testImplementation(libs.jaywayJsonPath)
    testImplementation(libs.selfie)
}
