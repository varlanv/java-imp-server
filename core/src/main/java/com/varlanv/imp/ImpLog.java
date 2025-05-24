package com.varlanv.imp;

import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

final class ImpLog {

    private static final Log log;

    private ImpLog() {}

    static void debug(Supplier<String> message) {
        log.debug(message);
    }

    static void error(Supplier<String> message, Exception exception) {
        log.error(message.get(), exception);
    }

    static void error(Exception e) {
        log.error(e.getMessage(), e);
    }

    interface Log {

        void debug(Supplier<String> message);

        void error(Supplier<String> message, Exception exception);

        void error(String message, Exception exception);
    }

    static {
        Log resultLog = null;
        try {
            Class.forName("org.slf4j.LoggerFactory");
            var logger = LoggerFactory.getLogger(ImpLog.class);
            if (!(logger instanceof NOPLogger)) {
                resultLog = new Log() {
                    @Override
                    public void debug(Supplier<String> message) {
                        logger.debug(message.get());
                    }

                    @Override
                    public void error(Supplier<String> message, Exception exception) {
                        logger.error(message.get(), exception);
                    }

                    @Override
                    public void error(String message, Exception exception) {
                        logger.error(message, exception);
                    }
                };
            }
        } catch (ClassNotFoundException ignored) {
            // noop
        }
        if (resultLog == null) {
            resultLog = new Log() {
                @Override
                public void debug(Supplier<String> message) {
                    // noop
                }

                @Override
                public void error(Supplier<String> message, Exception exception) {
                    // noop
                }

                @Override
                public void error(String message, Exception exception) {
                    // noop
                }
            };
        }
        log = resultLog;
    }
}
