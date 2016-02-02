/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */
package contracts;

import core.*;

import java.security.*;
import java.time.*;

/* This is an interface solely created to demonstrate that the same kotlin tests can be run against
 * either a Java implementation of the CommercialPaper or a kotlin implementation.
 * Normally one would not duplicate an implementation in different languages for obvious reasons, but it demonstrates that
 * ultimately either language can be used against a common test framework (and therefore can be used for real).
 */
public interface ICommercialPaperState extends ContractState {
    ICommercialPaperState withOwner(PublicKey newOwner);
    ICommercialPaperState withIssuance(PartyReference newIssuance);
    ICommercialPaperState withFaceValue(Amount newFaceValue);
    ICommercialPaperState withMaturityDate(Instant newMaturityDate);
}
