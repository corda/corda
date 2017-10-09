package net.corda.sandbox;

/**
 *
 */
public class WhitelistClassloadingException extends Exception {

    public WhitelistClassloadingException() {
        super();
    }

    public WhitelistClassloadingException(String message) {
        super(message);
    }

    public WhitelistClassloadingException(String message, Throwable cause) {
        super(message, cause);
    }

    public WhitelistClassloadingException(Throwable cause) {
        super(cause);
    }

    protected WhitelistClassloadingException(String message, Throwable cause,
                                             boolean enableSuppression,
                                             boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }


}
