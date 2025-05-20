package com.varlanv.imp;

interface ImpContentTypes {

    default ImpContentType json() {
        return ImpContentType.APPLICATION_JSON;
    }

    default ImpContentType xml() {
        return ImpContentType.APPLICATION_XML;
    }

    default ImpContentType text() {
        return ImpContentType.TEXT_PLAIN;
    }
}
