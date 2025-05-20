package com.varlanv.imp;

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(strictBuilder = true)
interface ServerConfig {

    ImpPort port();

    ResponseDecision decision();
}
