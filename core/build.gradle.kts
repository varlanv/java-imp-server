plugins {
    java
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.testKonvence)
}

dependencies {
    testImplementation("com.github.dreamhead:moco-runner:1.5.0")
}
