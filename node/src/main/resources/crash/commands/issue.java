package crash.commands;

import java.time.Duration;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.ContractsDSL;
import net.corda.core.crypto.Party;
import net.corda.core.flows.FlowLogic;
import net.corda.core.serialization.OpaqueBytes;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.Utils;
import net.corda.flows.IssuerFlow.IssuanceRequester;
import net.corda.node.internal.Node;
import net.corda.node.services.api.ServiceHubInternal;
import org.crsh.cli.Command;
import org.crsh.cli.Usage;
import org.crsh.command.BaseCommand;
import org.crsh.cli.Argument;
// import net.corda.node.cmdshell.ReflectionKt;


@Usage("issue cash")
public class issue extends BaseCommand {

    @Command
    public String main(@Argument String partyName, @Argument Integer amount) {

        if (partyName == null)
            return "Error: Missing parameters! \nUsage: 'issue Bank_A 1000'\n"
                    + "Note: Replace spaces in party names with underscores.";

        partyName = partyName.replace("_"," ");

        Node node = (Node) context.getAttributes().get("node");
        ServiceHubInternal services = node.getServices();

        Party issueToParty = services.getIdentityService().partyFromName(partyName);
        Party issuerBankParty = services.getIdentityService().partyFromName("BankOfCorda");

        if (issueToParty == null)
            return String.format("Unable to locate '%s' in Network Map Service", partyName);

        if (issuerBankParty == null)
            return "Unable to locate 'BankOfCorda' in Network Map Service";

        if (amount == null || amount < 0)
            return "Amount must be a positive integer.";

        Amount dollars = ContractsDSL.getDOLLARS(amount);
        OpaqueBytes bytes = OpaqueBytes.Companion.of(new byte[]{1, 2, 3});
        FlowLogic issuance = new IssuanceRequester(dollars, issueToParty, bytes, issuerBankParty);
        SignedTransaction tx = (SignedTransaction) Utils.getOrThrow(services.startFlow(issuance).getResultFuture(), (Duration) null);

        return tx.getId().toString();
    }
}

