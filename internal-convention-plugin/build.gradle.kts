import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import org.gradle.plugins.ide.idea.model.IdeaModel

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.spotless)
}

extensions.configure<SpotlessExtension>("spotless") {
    java {
        importOrder()
        removeUnusedImports()
        palantirJavaFormat()
        formatAnnotations()
        encoding("UTF-8")
        endWithNewline()
        setLineEndings(LineEnding.UNIX)
        trimTrailingWhitespace()
        cleanthat()
    }
}

val isCiBuild = providers.environmentVariable("CI").orNull != null

java {
    toolchain {
        vendor.set(JvmVendorSpec.ADOPTIUM)
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

if (!isCiBuild) {
    pluginManager.apply(IdeaPlugin::class.java)
    val idea = extensions.getByName("idea") as IdeaModel
    idea.module.isDownloadJavadoc = true
    idea.module.isDownloadSources = true
}

repositories {
    if (!isCiBuild) {
        mavenLocal()
    }
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.nullaway.plugin)
    implementation(libs.errorprone.plugin)
    implementation(libs.spotbugs.plugin)
    implementation(libs.spotless.plugin)
    implementation(libs.benmanes.version.plugin)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jSpecify)
    compileOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("internalGradleConventionPlugin") {
            id = libs.plugins.internalConvention.get().pluginId
            implementationClass = "com.varlanv.gradle.plugin.InternalConventionPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
