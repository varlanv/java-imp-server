package com.varlanv.imp;

import java.net.http.HttpHeaders;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
        throwForNullPointer = IllegalArgumentException.class,
        nullableAnnotation = "org.jspecify.annotations.Nullable")
public interface RequestMatch {

    @Value.Default
    default ImpPredicate<HttpHeaders> headersPredicate() {
        return ImpPredicate.alwaysTrue();
    }
}
