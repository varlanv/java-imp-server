package com.varlanv.imp;

import com.sun.net.httpserver.HttpExchange;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(strictBuilder = true, nullableAnnotation = "org.jspecify.annotations.Nullable")
interface TemplateConfig {

    FutureServer futureServer();

    ResponseDecision decision();

    ImpFn<HttpExchange, ImpResponse> fallback();
}
