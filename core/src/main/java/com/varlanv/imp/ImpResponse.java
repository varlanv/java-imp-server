package com.varlanv.imp;

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(nullableAnnotation = "org.jspecify.annotations.Nullable")
interface ImpResponse {

    ImpSupplier<byte[]> body();

    ImpHttpStatus statusCode();

    @Value.Default
    default HeadersOperator headers() {
        return headers -> headers;
    }
}
