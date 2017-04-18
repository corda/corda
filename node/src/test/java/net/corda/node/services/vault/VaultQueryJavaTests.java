package net.corda.node.services.vault;

import io.requery.query.*;
import kotlin.*;
import net.corda.contracts.asset.*;
import net.corda.core.contracts.*;
import net.corda.core.crypto.*;
import net.corda.core.node.services.*;
import net.corda.core.node.services.vault.*;
import net.corda.core.serialization.*;
import net.corda.core.transactions.*;
import net.corda.testing.node.*;
import net.corda.core.node.services.vault.QueryCriteria.*;
import org.jetbrains.annotations.*;
import org.jetbrains.exposed.sql.*;
import org.junit.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import static net.corda.testing.node.MockServicesKt.makeTestDataSourceProperties;
import static net.corda.node.utilities.DatabaseSupportKt.configureDatabase;
import static net.corda.node.utilities.DatabaseSupportKt.databaseTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static net.corda.contracts.testing.VaultFiller.fillWithSomeTestCash;
import static net.corda.contracts.testing.VaultFiller.fillWithSomeTestDeals;
import static net.corda.contracts.testing.VaultFiller.fillWithSomeTestLinearStates;
import static net.corda.core.utilities.TestConstants.getDUMMY_NOTARY;
import static net.corda.contracts.asset.CashKt.getDUMMY_CASH_ISSUER;
import static net.corda.contracts.asset.CashKt.getDUMMY_CASH_ISSUER_KEY;
import static net.corda.testing.CoreTestUtils.getMEGA_CORP;
import static net.corda.core.node.services.vault.QueryCriteriaKt.and;

public class VaultQueryJavaTests {

    private MockServices services;
    private VaultService vaultSvc;
    private Closeable dataSource;
    private Database database;

    @Before
    public void setUp() {

        Properties dataSourceProps = makeTestDataSourceProperties(SecureHash.randomSHA256().toString());
        Pair<Closeable, Database> dataSourceAndDatabase = configureDatabase(dataSourceProps);
        dataSource = dataSourceAndDatabase.getFirst();
        database = dataSourceAndDatabase.getSecond();

        databaseTransaction(database, statement -> services = new MockServices() {
            @NotNull
            @Override
            public VaultService getVaultService() {
                return makeVaultService(dataSourceProps);
            }

            @Override
            public void recordTransactions(@NotNull Iterable<SignedTransaction> txs) {
                for (SignedTransaction stx : txs ) {
                    getStorageService().getValidatedTransactions().addTransaction(stx);
                }

                Stream<WireTransaction> wtxn = StreamSupport.stream(txs.spliterator(), false).map(txn -> txn.getTx());
                getVaultService().notifyAll(wtxn.collect(Collectors.toList()));
            }
        });

        vaultSvc = services.getVaultService();
    }

    @After
    public void cleanUp() throws IOException {
        dataSource.close();
    }

    /**
     * Sample Vault Query API tests
     */

    @Test
    public void consumedStates() {
        databaseTransaction(database, tx -> {
            fillWithSomeTestCash(services,
                                 new Amount(100, Currency.getInstance("USD")),
                                 getDUMMY_NOTARY(),
                                3,
                                3,
                                 new Random(),
                                 new OpaqueBytes("1".getBytes()),
                                null,
                                 getDUMMY_CASH_ISSUER(),
                                 getDUMMY_CASH_ISSUER_KEY() );

            // DOCSTART VaultJavaQueryExample1
            Set contractStateTypes = new HashSet(Arrays.asList(Cash.State.class));
            Vault.StateStatus status = Vault.StateStatus.CONSUMED;

            VaultQueryCriteria criteria1 = new VaultQueryCriteria(status, contractStateTypes);
            PageSpecification pageSpec  = new VaultQueryCriteria.PageSpecification(1, 100);
            Order order = Order.ASC;
            Iterable<StateAndRef<ContractState>> states = vaultSvc.queryBy(criteria1, pageSpec, order);
            // DOCEND VaultJavaQueryExample1

            assertThat(states).hasSize(3);

            return tx;
        });
    }

    @Test
    public void consumedDealStates() {
        databaseTransaction(database, tx -> {

            UniqueIdentifier uid = new UniqueIdentifier();
            fillWithSomeTestLinearStates(services, 10, uid);

            List<String> dealIds = Arrays.asList("123", "456", "789");
            fillWithSomeTestDeals(services, dealIds, 0);

            // DOCSTART VaultJavaQueryExample2
            Vault.StateStatus status = Vault.StateStatus.CONSUMED;
            HashSet contractStateTypes = new HashSet(Arrays.asList(DealState.class));
            QueryCriteria vaultCriteria = new VaultQueryCriteria(status, contractStateTypes);

            List<UniqueIdentifier> linearIds = Arrays.asList(uid);
            List<Party> dealParties = Arrays.asList(getMEGA_CORP());
            QueryCriteria dealCriteriaAll = new LinearStateQueryCriteria(linearIds, false, dealIds, dealParties);

            QueryCriteria compositeCriteria = and(dealCriteriaAll, vaultCriteria);

            PageSpecification pageSpec  = new VaultQueryCriteria.PageSpecification(1, 100);
            Order order = Order.ASC;
            Iterable<StateAndRef<ContractState>> states = vaultSvc.queryBy(compositeCriteria, pageSpec, order);
            // DOCEND VaultJavaQueryExample2

            assertThat(states).hasSize(4);

            return tx;
        })
    }

    /**
     * Deprecated usage
     */

    @Test
    public void consumedStatesDeprecated() {
        databaseTransaction(database, tx -> {
            fillWithSomeTestCash(services,
                    new Amount(100, Currency.getInstance("USD")),
                    getDUMMY_NOTARY(),
                    3,
                    3,
                    new Random(),
                    new OpaqueBytes("1".getBytes()),
                    null,
                    getDUMMY_CASH_ISSUER(),
                    getDUMMY_CASH_ISSUER_KEY() );

            // DOCSTART VaultDeprecatedJavaQueryExample1
            Set contractStateTypes = new HashSet(Arrays.asList(Cash.State.class));
            EnumSet<Vault.StateStatus> status = EnumSet.of(Vault.StateStatus.CONSUMED);

            // WARNING! unfortunately cannot use inlined reified Kotlin extension methods.
            Iterable<StateAndRef<ContractState>> states = vaultSvc.states(contractStateTypes, status, true);
            // DOCEND VaultDeprecatedJavaQueryExample1

            assertThat(states).hasSize(3);

            return tx;
        });
    }

    @Test
    public void consumedStatesForLinearIdDeprecated() {
        databaseTransaction(database, tx -> {

            UniqueIdentifier trackUid = new UniqueIdentifier();
            fillWithSomeTestLinearStates(services, 1, trackUid);
            fillWithSomeTestLinearStates(services, 4, new UniqueIdentifier());

            // DOCSTART VaultDeprecatedJavaQueryExample2
            Set contractStateTypes = new HashSet(Arrays.asList(LinearState.class));
            EnumSet<Vault.StateStatus> status = EnumSet.of(Vault.StateStatus.CONSUMED);

            // WARNING! unfortunately cannot use inlined reified Kotlin extension methods.
            Iterable<StateAndRef<ContractState>> states = vaultSvc.states(contractStateTypes, status, true);

            Stream<StateAndRef<ContractState>> trackedLinearState = StreamSupport.stream(states.spliterator(), false).filter(
                    state -> ((LinearState) state.component1().getData()).getLinearId() == trackUid);
            // DOCEND VaultDeprecatedJavaQueryExample2

            assertThat(states).hasSize(4);
            assertThat(trackedLinearState).hasSize(1);

            return tx;
        });
    }
}
