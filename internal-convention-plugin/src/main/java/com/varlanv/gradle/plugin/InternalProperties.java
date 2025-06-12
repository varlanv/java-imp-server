package com.varlanv.gradle.plugin;

import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.plugin.use.PluginDependency;

class InternalProperties {

    private final VersionCatalog versionCatalog;

    public InternalProperties(VersionCatalogsExtension versionCatalogsExtension) {
        this.versionCatalog = versionCatalogsExtension.named("libs");
    }

    public static String name() {
        return "__internal_convention_properties__";
    }

    public Provider<MinimalExternalModuleDependency> getLib(String name) {
        return versionCatalog
                .findLibrary(name)
                .orElseThrow(() -> new IllegalStateException("Unable to find library [%s]".formatted(name)));
    }

    public Provider<PluginDependency> getPlugin(String name) {
        return versionCatalog
                .findPlugin("errorProne")
                .orElseThrow(() -> new IllegalStateException("Unable to find plugin [%s]".formatted(name)));
    }

    public String getVersion(String name) {
        return versionCatalog
                .findVersion(name)
                .map(VersionConstraint::getRequiredVersion)
                .orElseThrow(() -> new IllegalStateException("Unable to find version [%s]".formatted(name)));
    }
}
