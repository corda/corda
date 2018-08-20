package net.corda.tools.shell;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Lists;
import kotlin.Pair;
import net.corda.client.jackson.JacksonSupport;
import net.corda.client.jackson.internal.ToStringSerialize;
import net.corda.core.contracts.Amount;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.StateMachineRunId;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.internal.concurrent.CordaFutureImplKt;
import net.corda.core.internal.concurrent.OpenFuture;
import net.corda.core.messaging.FlowProgressHandleImpl;
import net.corda.core.utilities.ProgressTracker;
import net.corda.node.services.identity.InMemoryIdentityService;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.internal.InternalTestConstantsKt;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import rx.Observable;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

public class InteractiveShellJavaTest {
    private static TestIdentity megaCorp = new TestIdentity(new CordaX500Name("MegaCorp", "London", "GB"));

    // should guarantee that FlowA will have synthetic method to access this field
    private static final String synthetic = "synth";

    abstract static class StringFlow extends FlowLogic<String> {
        abstract String getA();
    }

    @SuppressWarnings("unused")
    public static class FlowA extends StringFlow {

        private String a;

        public FlowA(String a) {
            if (!synthetic.isEmpty()) {
                this.a = a;
            }
        }

        public FlowA(Integer b) {
            this(b.toString());
        }

        public FlowA(Integer b, String c) {
            this(b.toString() + c);
        }

        public FlowA(Amount<Currency> amount) {
            this(amount.toString());
        }

        public FlowA(Pair<Amount<Currency>, SecureHash.SHA256> pair) {
            this(pair.toString());
        }

        public FlowA(Party party) {
            this(party.getName().toString());
        }

        public FlowA(Integer b, Amount<UserValue> amount) {
            this(String.format("%d %s", amount.getQuantity() + (b == null ? 0 : b), amount.getToken()));
        }

        public FlowA(String[] b) {
            this(String.join("+", b));
        }

        public FlowA(Amount<UserValue>[] amounts) {
            this(String.join("++", Arrays.stream(amounts).map(Amount::toString).collect(toList())));
        }

        @Nullable
        @Override
        public ProgressTracker getProgressTracker() {
            return new ProgressTracker();
        }

        @Override
        public String call() {
            return a;
        }

        @Override
        String getA() {
            return a;
        }
    }

    public static class FlowB extends StringFlow {

        private Party party;
        private String a;

        public FlowB(Party party, String a) {
            this.party = party;
            this.a = a;
        }

        @Nullable
        @Override
        public ProgressTracker getProgressTracker() {
            return new ProgressTracker();
        }

        @Override
        public String call() throws FlowException {
            FlowSession session = initiateFlow(party);


            Integer integer = session.receive(Integer.class).unwrap((i) -> i);

            return integer.toString();

        }

        @Override
        String getA() {
            return a;
        }
    }

    @ToStringSerialize
    public static class UserValue {
        private final String label;

        public UserValue(@JsonProperty("label") String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private InMemoryIdentityService ids = new InMemoryIdentityService(Lists.newArrayList(megaCorp.getIdentity()), InternalTestConstantsKt.getDEV_ROOT_CA().getCertificate());

    private ObjectMapper om = JacksonSupport.createInMemoryMapper(ids, new YAMLFactory());

    private String output;

    private void check(String input, String expected, Class<? extends StringFlow> flowClass) throws InteractiveShell.NoApplicableConstructor {
        InteractiveShell.INSTANCE.runFlowFromString((clazz, args) -> {

            StringFlow instance = null;
            try {
                instance = (StringFlow)clazz.getConstructor(Arrays.stream(args).map(Object::getClass).toArray(Class[]::new)).newInstance(args);
            } catch (Exception e) {
                System.out.println(e);
            }
            output = instance.getA();
            OpenFuture<String> future = CordaFutureImplKt.openFuture();
            future.set("ABC");
            return new FlowProgressHandleImpl(StateMachineRunId.Companion.createRandom(), future, Observable.just("Some string"));
        }, input, flowClass, om);
        assertEquals(input, expected, output);
    }

    @Test
    public void flowStartSimple() throws InteractiveShell.NoApplicableConstructor {
        check("a: Hi there", "Hi there", FlowA.class);
        check("b: 12", "12", FlowA.class);
        check("b: 12, c: Yo", "12Yo", FlowA.class);
    }

    @Test
    public void flowStartWithComplexTypes() throws InteractiveShell.NoApplicableConstructor {
        check("amount: £10", "10.00 GBP", FlowA.class);
    }

    @Test
    public void flowStartWithNestedTypes() throws InteractiveShell.NoApplicableConstructor {
        check(
            "pair: { first: $100.12, second: df489807f81c8c8829e509e1bcb92e6692b9dd9d624b7456435cb2f51dc82587 }",
            "(100.12 USD, DF489807F81C8C8829E509E1BCB92E6692B9DD9D624B7456435CB2F51DC82587)",
            FlowA.class);
    }

    @Test
    public void flowStartWithUserAmount() throws InteractiveShell.NoApplicableConstructor {
        check(
            "b: 500, amount: { \"quantity\": 10001, \"token\":{ \"label\": \"of value\" } }",
            "10501 of value",
            FlowA.class);
    }

    @Test
    public void flowStartWithArrayType() throws InteractiveShell.NoApplicableConstructor {
        check(
            "b: [ One, Two, Three, Four ]",
            "One+Two+Three+Four",
            FlowA.class
        );
    }

    @Test
    public void flowStartWithArrayOfNestedType() throws InteractiveShell.NoApplicableConstructor {
        check(
            "amounts: [ { \"quantity\": 10, \"token\": { \"label\": \"(1)\" } }, { \"quantity\": 200, \"token\": { \"label\": \"(2)\" } } ]",
            "10 (1)++200 (2)",
            FlowA.class
        );
    }

    @Test(expected = InteractiveShell.NoApplicableConstructor.class)
    public void flowStartNoArgs() throws InteractiveShell.NoApplicableConstructor {
        check("", "", FlowA.class);
    }

    @Test(expected = InteractiveShell.NoApplicableConstructor.class)
    public void flowMissingParam() throws InteractiveShell.NoApplicableConstructor {
        check("c: Yo", "", FlowA.class);
    }

    @Test(expected = InteractiveShell.NoApplicableConstructor.class)
    public void flowTooManyParams() throws InteractiveShell.NoApplicableConstructor {
        check("b: 12, c: Yo, d: Bar", "", FlowA.class);
    }

    @Test
    public void party() throws InteractiveShell.NoApplicableConstructor {
        check("party: \"" + megaCorp.getName() + "\"", megaCorp.getName().toString(), FlowA.class);
    }

    @Test
    public void unwrapLambda() throws InteractiveShell.NoApplicableConstructor {
        check("party: \"" + megaCorp.getName() + "\", a: Bambam", "Bambam", FlowB.class);
    }
}
