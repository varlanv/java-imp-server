package com.varlanv.imp;

public interface ImpServer {

    static ImpTemplateSpec.Start template() {
        return new ImpTemplateSpec.Start();
    }

    int port();

    ImpStatistics statistics();
}
