/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.crypto;

public class AddressFormatException extends IllegalArgumentException {
    public AddressFormatException() {
        super();
    }
    public AddressFormatException(String message) {
        super(message);
    }
}
