package net.corda.testing.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public final class Retry {
    private static final Logger log = LoggerFactory.getLogger(Retry.class);

    public interface RetryStrategy {
        <T> T run(Callable<T> op) throws RetryException;
    }

    public static final class RetryException extends RuntimeException {
        public RetryException(String message) {
            super(message);
        }

        public RetryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static RetryStrategy fixed(int times) {
        return new RetryStrategy() {
            @Override
            public <T> T run(Callable<T> op) {
                int run = 0;
                Exception last = null;
                while (run < times) {
                    try {
                        return op.call();
                    } catch (Exception e) {
                        last = e;
                        log.info("Exception caught: " + e.getMessage());
                    }
                    run++;
                }
                throw new RetryException("Operation failed " + run + " times", last);
            }
        };
    }
}



