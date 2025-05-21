package com.varlanv.imp;

import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
interface ImpResponse {

    ImpSupplier<byte[]> body();

    ImpHttpStatus statusCode();

    @Value.Default
    default Map<String, List<String>> headers() {
        return Map.of();
    }
}
