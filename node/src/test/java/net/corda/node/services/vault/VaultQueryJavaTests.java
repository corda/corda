package net.corda.node.services.vault;

import com.google.common.collect.*;
import kotlin.*;
import net.corda.contracts.asset.*;
import net.corda.core.contracts.*;
import net.corda.core.crypto.*;
import net.corda.core.node.services.*;
import net.corda.core.node.services.vault.*;
import net.corda.core.node.services.vault.QueryCriteria.*;
import net.corda.core.serialization.*;
import net.corda.core.transactions.*;
import net.corda.node.services.vault.schemas.*;
import net.corda.testing.node.*;
import org.jetbrains.annotations.*;
import org.jetbrains.exposed.sql.*;
import org.junit.*;
import rx.Observable;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import static net.corda.contracts.asset.CashKt.*;
import static net.corda.contracts.testing.VaultFiller.*;
import static net.corda.core.node.services.vault.QueryCriteriaKt.*;
import static net.corda.core.node.services.vault.QueryCriteriaUtilsKt.*;
import static net.corda.core.utilities.TestConstants.*;
import static net.corda.node.utilities.DatabaseSupportKt.*;
import static net.corda.node.utilities.DatabaseSupportKt.transaction;
import static net.corda.testing.CoreTestUtils.*;
import static net.corda.testing.node.MockServicesKt.*;
import static org.assertj.core.api.Assertions.*;

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

        transaction(database, statement -> services = new MockServices() {
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

    /**
     *  Static queryBy() tests
     */

    @Test
    public void consumedStates() {
        transaction(database, tx -> {
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
            List contractStateTypes = Arrays.asList(Cash.State.class);
            Vault.StateStatus status = Vault.StateStatus.CONSUMED;

            VaultQueryCriteria criteria = new VaultQueryCriteria(status, contractStateTypes);
            Vault.Page<ContractState> results = vaultSvc.queryBy(criteria);
            // DOCEND VaultJavaQueryExample1

            assertThat(results.getStates()).hasSize(3);

            return tx;
        });
    }

    @Test
    public void consumedDealStatesPagedSorted() {
        transaction(database, tx -> {

            UniqueIdentifier uid = new UniqueIdentifier();
            fillWithSomeTestLinearStates(services, 10, uid);

            List<String> dealIds = Arrays.asList("123", "456", "789");
            fillWithSomeTestDeals(services, dealIds, 0);

            // DOCSTART VaultJavaQueryExample2
            Vault.StateStatus status = Vault.StateStatus.CONSUMED;
            List contractStateTypes = Arrays.asList(DealState.class);
            QueryCriteria vaultCriteria = new VaultQueryCriteria(status, contractStateTypes);

            List<UniqueIdentifier> linearIds = Arrays.asList(uid);
            List<String> dealPartyNames = Arrays.asList(getMEGA_CORP().getName());
            QueryCriteria dealCriteriaAll = new LinearStateQueryCriteria(linearIds, false, dealIds, dealPartyNames);

            QueryCriteria compositeCriteria = and(dealCriteriaAll, vaultCriteria);

            PageSpecification pageSpec  = new PageSpecification(0, getMAX_PAGE_SIZE());
            Sort.SortColumn sortByUid = new Sort.SortColumn(VaultLinearStateEntity.UUID.getName(), Sort.Direction.DESC, Sort.NullHandling.NULLS_LAST);
            Sort sorting = new Sort(ImmutableSet.of(sortByUid));
            Vault.Page<ContractState> results = vaultSvc.queryBy(compositeCriteria, pageSpec, sorting);
            // DOCEND VaultJavaQueryExample2

            assertThat(results.getStates()).hasSize(4);

            return tx;
        });
    }

    /**
     *  Dynamic trackBy() tests
     */

    @Test
    public void trackCashStates() {

        transaction(database, tx -> {
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
            List contractStateTypes = Arrays.asList(Cash.State.class);

            VaultQueryCriteria criteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, contractStateTypes);
            Vault.PageAndUpdates<ContractState> results = vaultSvc.trackBy(criteria);

            Vault.Page<ContractState> snapshot = results.getCurrent();
            Observable<Vault.Update> updates = results.getFuture();

            // DOCEND VaultJavaQueryExample1
            assertThat(snapshot.getStates()).hasSize(3);

            return tx;
        });
    }

    @Test
    public void trackDealStatesPagedSorted() {
        transaction(database, tx -> {

            UniqueIdentifier uid = new UniqueIdentifier();
            fillWithSomeTestLinearStates(services, 10, uid);

            List<String> dealIds = Arrays.asList("123", "456", "789");
            fillWithSomeTestDeals(services, dealIds, 0);

            // DOCSTART VaultJavaQueryExample2
            List contractStateTypes = Arrays.asList(DealState.class);
            QueryCriteria vaultCriteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, contractStateTypes);

            List<UniqueIdentifier> linearIds = Arrays.asList(uid);
            List<String> dealPartyNames = Arrays.asList(getMEGA_CORP().getName());
            QueryCriteria dealCriteriaAll = new LinearStateQueryCriteria(linearIds, false, dealIds, dealPartyNames);

            QueryCriteria compositeCriteria = and(dealCriteriaAll, vaultCriteria);

            PageSpecification pageSpec  = new PageSpecification(0, getMAX_PAGE_SIZE());
            Sort.SortColumn sortByUid = new Sort.SortColumn(VaultLinearStateEntity.UUID.getName(), Sort.Direction.DESC, Sort.NullHandling.NULLS_LAST);
            Sort sorting = new Sort(ImmutableSet.of(sortByUid));
            Vault.PageAndUpdates<ContractState> results = vaultSvc.trackBy(compositeCriteria, pageSpec, sorting);

            Vault.Page<ContractState> snapshot = results.getCurrent();
            Observable<Vault.Update> updates = results.getFuture();
            // DOCEND VaultJavaQueryExample2

            assertThat(snapshot.getStates()).hasSize(4);

            return tx;
        });
    }

    /**
     * Deprecated usage
     */

    @Test
    public void consumedStatesDeprecated() {
        transaction(database, tx -> {
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
            Iterable<StateAndRef<ContractState>> results = vaultSvc.states(contractStateTypes, status, true);
            // DOCEND VaultDeprecatedJavaQueryExample1

            assertThat(results).hasSize(3);

            return tx;
        });
    }

    @Test
    public void consumedStatesForLinearIdDeprecated() {
        transaction(database, tx -> {

            UniqueIdentifier trackUid = new UniqueIdentifier();
            fillWithSomeTestLinearStates(services, 1, trackUid);
            fillWithSomeTestLinearStates(services, 4, new UniqueIdentifier());

            // DOCSTART VaultDeprecatedJavaQueryExample2
            Set contractStateTypes = new HashSet(Arrays.asList(LinearState.class));
            EnumSet<Vault.StateStatus> status = EnumSet.of(Vault.StateStatus.CONSUMED);

            // WARNING! unfortunately cannot use inlined reified Kotlin extension methods.
            Iterable<StateAndRef<ContractState>> results = vaultSvc.states(contractStateTypes, status, true);

            Stream<StateAndRef<ContractState>> trackedLinearState = StreamSupport.stream(results.spliterator(), false).filter(
                    state -> ((LinearState) state.component1().getData()).getLinearId() == trackUid);
            // DOCEND VaultDeprecatedJavaQueryExample2

            assertThat(results).hasSize(4);
            assertThat(trackedLinearState).hasSize(1);

            return tx;
        });
    }
}
