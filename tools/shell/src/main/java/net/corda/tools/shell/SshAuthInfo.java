package net.corda.tools.shell;

import net.corda.core.messaging.RPCOps;
import org.crsh.auth.AuthInfo;

public interface SshAuthInfo extends AuthInfo {
    <T extends RPCOps> T getOrCreateRpcOps(Class<T> rpcOpsClass);
}