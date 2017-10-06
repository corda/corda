package net.corda.sandbox.costing;

/**
 * This interface is to decouple the actual executable code from the entry point and
 * how vetted deterministic code will be used inside the sandbox
 *
 * @author ben
 */
public interface ContractExecutor {
    /**
     * Executes a smart contract
     *
     * @param contract the contract to be executed
     */
    void execute(Contract contract);

    /**
     * Checks to see if the supplied Contract is suitable
     *
     * @param contract
     * @return true if the contract is suitable for execution
     */
    boolean isSuitable(Contract contract);
}
