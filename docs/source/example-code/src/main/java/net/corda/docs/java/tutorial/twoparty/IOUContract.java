/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.docs.java.tutorial.twoparty;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

// DOCSTART 01
// Add these imports:
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.identity.Party;

import java.security.PublicKey;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

// Replace TemplateContract's definition with:
public class IOUContract implements Contract {
    public static final String IOU_CONTRACT_ID = "com.template.IOUContract";

    // Our Create command.
    public static class Create implements CommandData {
    }

    @Override
    public void verify(LedgerTransaction tx) {
        final CommandWithParties<IOUContract.Create> command = requireSingleCommand(tx.getCommands(), IOUContract.Create.class);

        requireThat(check -> {
            // Constraints on the shape of the transaction.
            check.using("No inputs should be consumed when issuing an IOU.", tx.getInputs().isEmpty());
            check.using("There should be one output state of type IOUState.", tx.getOutputs().size() == 1);

            // IOU-specific constraints.
            final IOUState out = tx.outputsOfType(IOUState.class).get(0);
            final Party lender = out.getLender();
            final Party borrower = out.getBorrower();
            check.using("The IOU's value must be non-negative.", out.getValue() > 0);
            check.using("The lender and the borrower cannot be the same entity.", lender != borrower);

            // Constraints on the signers.
            final List<PublicKey> signers = command.getSigners();
            check.using("There must be two signers.", signers.size() == 2);
            check.using("The borrower and lender must be signers.", signers.containsAll(
                    ImmutableList.of(borrower.getOwningKey(), lender.getOwningKey())));

            return null;
        });
    }
}
// DOCEND 01