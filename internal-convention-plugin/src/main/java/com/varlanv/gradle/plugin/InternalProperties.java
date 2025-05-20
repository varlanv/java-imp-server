package com.varlanv.gradle.plugin;

import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.provider.Provider;

class InternalProperties {

    private final VersionCatalog versionCatalog;

    public InternalProperties(VersionCatalog versionCatalog) {
        this.versionCatalog = versionCatalog;
    }

    public static String name() {
        return "__internal_convention_properties__";
    }

    public String getLib(String name) {
        return versionCatalog.findLibrary(name)
            .map(maybeLib -> maybeLib.map(lib -> String.format("%s:%s:%s", lib.getGroup(), lib.getName(), lib.getVersion())))
            .map(Provider::getOrNull)
            .orElseThrow(() -> new IllegalStateException("Unable to find library [%s]".formatted(name)));

    }

    public String getVersion(String name) {
        return versionCatalog.findVersion(name)
            .map(VersionConstraint::getRequiredVersion)
            .orElseThrow(() -> new IllegalStateException("Unable to find version [%s]".formatted(name)));
    }
}
