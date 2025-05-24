package com.varlanv.imp;

public interface ImpServer {

    static ImpTemplateSpec.Start httpTemplate() {
        return new ImpTemplateSpec.Start();
    }

    int port();

    ImpStatistics statistics();
}
