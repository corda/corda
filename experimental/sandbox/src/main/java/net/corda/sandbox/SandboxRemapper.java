package net.corda.sandbox;

import org.objectweb.asm.commons.Remapper;

/**
 * @author ben
 */
public final class SandboxRemapper extends Remapper {

    @Override
    public String mapDesc(final String desc) {
        return super.mapDesc(Utils.rewriteDescInternal(desc));
    }

    @Override
    public String map(final String typename) {
        return super.map(Utils.sandboxInternalTypeName(typename));
    }
}
