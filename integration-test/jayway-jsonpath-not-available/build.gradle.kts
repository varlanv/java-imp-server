plugins {
    `java-library`
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.testKonvence)
}

internalConvention {
    internalModule = true
}

dependencies {
    testImplementation(projects.core)
}
