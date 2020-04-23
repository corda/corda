// We purposefully have this template here as part of progressing through the tutorial
package com.template;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

public class TemplateContract implements Contract {
    // This is used to identify our contract when building a transaction.
    public static final String ID = "com.template.TemplateContract";

    /**
     * A transaction is considered valid if the verify() function of the contract of each of the transaction's input
     * and output states does not throw an exception.
     */
    @Override
    public void verify(@NotNull LedgerTransaction tx) {}

    public interface Commands extends CommandData {
        class Action implements Commands {}
    }
}
