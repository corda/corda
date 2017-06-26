// Due to Capsule being in the default package, which cannot be imported, this caplet
// must also be in the default package. When using Kotlin there are a whole host of exceptions
// trying to construct this from Capsule, so it is written in Java.

import sun.misc.Signal;

public class CordaCaplet extends AbstractCordaCaplet {
    protected CordaCaplet(Capsule pred) {
        super(pred);
    }

    @Override
    protected void liftoff() {
        super.liftoff();
        Signal.handle(new Signal("INT"), signal -> {
            // Disable Ctrl-C for this process, so the child process can handle it in the shell instead.
        });
    }
}
