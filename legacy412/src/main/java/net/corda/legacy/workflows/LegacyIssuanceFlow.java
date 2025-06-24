package net.corda.legacy.workflows;

import co.paralleluniverse.fibers.Suspendable;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;
import net.corda.core.contracts.AttachmentResolutionException;
import net.corda.core.contracts.StateRef;
import net.corda.core.contracts.TransactionResolutionException;
import net.corda.core.contracts.TransactionVerificationException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.node.ServiceHub;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.legacy.contracts.AnotherDummyContract;
import org.jetbrains.annotations.NotNull;

import java.security.SignatureException;

@StartableByRPC
public final class LegacyIssuanceFlow extends FlowLogic {
    private final int magicNumber;

    public LegacyIssuanceFlow(int magicNumber) {
        this.magicNumber = magicNumber;
    }

    @Suspendable
    @NotNull
    public StateRef call() {
        ServiceHub var10000 = this.getServiceHub();
        AnotherDummyContract var10001 = new AnotherDummyContract();
        Party var10002 = this.getOurIdentity();
        byte[] var3 = new byte[]{0};
        TransactionBuilder var2 = var10001.generateInitial(var10002.ref(var3), this.magicNumber, (Party) CollectionsKt.first(this.getServiceHub().getNetworkMapCache().getNotaryIdentities()));
        Intrinsics.checkNotNullExpressionValue(var2, "generateInitial(...)");
        SignedTransaction stx = var10000.signInitialTransaction(var2);
        try {
            stx.verify(this.getServiceHub(), false);
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        } catch (AttachmentResolutionException e) {
            throw new RuntimeException(e);
        } catch (TransactionResolutionException e) {
            throw new RuntimeException(e);
        } catch (TransactionVerificationException e) {
            throw new RuntimeException(e);
        }
        //SignedTransaction.verify$default(stx, this.getServiceHub(), false, 2, (Object)null);
        this.getServiceHub().recordTransactions(stx, new SignedTransaction[0]);
        return stx.getTx().outRef(0).getRef();
    }
}
