package com.varlanv.imp.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("com.varlanv.imp.client")
public record ClientProperties(int port) {}
