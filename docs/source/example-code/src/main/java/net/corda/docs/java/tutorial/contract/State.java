/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.docs.java.tutorial.contract;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.*;
import net.corda.core.crypto.NullKeys;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

// DOCSTART 1
public class State implements OwnableState {
    private PartyAndReference issuance;
    private AbstractParty owner;
    private Amount<Issued<Currency>> faceValue;
    private Instant maturityDate;

    public State() {
    }  // For serialization

    public State(PartyAndReference issuance, AbstractParty owner, Amount<Issued<Currency>> faceValue,
                 Instant maturityDate) {
        this.issuance = issuance;
        this.owner = owner;
        this.faceValue = faceValue;
        this.maturityDate = maturityDate;
    }

    public State copy() {
        return new State(this.issuance, this.owner, this.faceValue, this.maturityDate);
    }

    public State withoutOwner() {
        return new State(this.issuance, new AnonymousParty(NullKeys.NullPublicKey.INSTANCE), this.faceValue, this.maturityDate);
    }

    @NotNull
    @Override
    public CommandAndState withNewOwner(@NotNull AbstractParty newOwner) {
        return new CommandAndState(new CommercialPaper.Commands.Move(), new State(this.issuance, newOwner, this.faceValue, this.maturityDate));
    }

    public PartyAndReference getIssuance() {
        return issuance;
    }

    public AbstractParty getOwner() {
        return owner;
    }

    public Amount<Issued<Currency>> getFaceValue() {
        return faceValue;
    }

    public Instant getMaturityDate() {
        return maturityDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        State state = (State) o;

        if (issuance != null ? !issuance.equals(state.issuance) : state.issuance != null) return false;
        if (owner != null ? !owner.equals(state.owner) : state.owner != null) return false;
        if (faceValue != null ? !faceValue.equals(state.faceValue) : state.faceValue != null) return false;
        return !(maturityDate != null ? !maturityDate.equals(state.maturityDate) : state.maturityDate != null);
    }

    @Override
    public int hashCode() {
        int result = issuance != null ? issuance.hashCode() : 0;
        result = 31 * result + (owner != null ? owner.hashCode() : 0);
        result = 31 * result + (faceValue != null ? faceValue.hashCode() : 0);
        result = 31 * result + (maturityDate != null ? maturityDate.hashCode() : 0);
        return result;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(this.owner);
    }
}
// DOCEND 1