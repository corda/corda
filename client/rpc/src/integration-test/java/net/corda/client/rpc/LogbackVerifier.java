package net.corda.client.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.setAllowComparingPrivateFields;

import net.corda.client.rpc.internal.ReconnectingCordaRPCOps;
import net.corda.core.utilities.NetworkHostAndPort;
import net.corda.finance.flows.TwoPartyTradeFlow;
import net.corda.node.services.Permissions;
import net.corda.testing.node.User;
import nl.altindag.log.LogCaptor;
import org.junit.Test;
import sun.tools.tree.InlineNewInstanceExpression;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

public class LogbackVerifier {

    @Test
    public void logInfoAndWarnMessages() {

        String expectedInfoMessage = "Keyboard not responding. Press any key to continue...";
        String expectedDebugMessage = "Attempting to connect.";
        String expectedWarnMessage = "Congratulations, you are pregnant!";

        LogCaptor<ReconnectingCordaRPCOps> logCaptor = LogCaptor.forClass(ReconnectingCordaRPCOps.class);

        CordaRPCClientConfiguration  config = CordaRPCClientConfiguration.DEFAULT.copy(
                CordaRPCClientConfiguration.DEFAULT.getConnectionMaxRetryInterval(),
                CordaRPCClientConfiguration.DEFAULT.getMinimumServerProtocolVersion(),
                CordaRPCClientConfiguration.DEFAULT.getTrackRpcCallSites(),
                CordaRPCClientConfiguration.DEFAULT.getReapInterval(),
                CordaRPCClientConfiguration.DEFAULT.getObservationExecutorPoolSize(),
                1,
                Duration.ofSeconds(1),
                1.0,
                2,
                CordaRPCClientConfiguration.DEFAULT.getMaxFileSize(),
                CordaRPCClientConfiguration.DEFAULT.getDeduplicationCacheExpiry()
        );
        Set<String> permissions = new HashSet<>();
        permissions.add(Permissions.all());
        User rpcUser = new User("user1", "test", new HashSet<String>(permissions));
        CordaRPCConnection connection = new CordaRPCClient(new NetworkHostAndPort("localhost", 42), config).start(rpcUser.getUsername(), rpcUser.getPassword(), new GracefulReconnect());


       // assertThat(logCaptor.getLogs("debug").contains(expectedDebugMessage));
    }
}
