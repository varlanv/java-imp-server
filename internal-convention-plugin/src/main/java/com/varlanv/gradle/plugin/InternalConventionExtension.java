package com.varlanv.gradle.plugin;

import org.gradle.api.provider.Property;

public interface InternalConventionExtension {

    static String name() {
        return "internalConvention";
    }

    Property<String> getIntegrationTestName();

    Property<Boolean> getInternalModule();

    Property<Boolean> getAddSlf4jApiDependency();
}
