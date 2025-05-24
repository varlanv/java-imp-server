plugins {
    java
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.testKonvence)
}

internalConvention {
    addSlf4jApiDependency = true
}

dependencies {
//    testImplementation("com.github.dreamhead:moco-runner:1.5.0")
//    testImplementation("org.wiremock:wiremock:3.13.0")
}
