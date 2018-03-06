/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
