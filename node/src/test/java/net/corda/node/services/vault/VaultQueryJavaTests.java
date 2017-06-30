package net.corda.node.services.vault;

import com.google.common.collect.*;
import kotlin.*;
import net.corda.contracts.*;
import net.corda.contracts.asset.*;
import net.corda.core.contracts.*;
import net.corda.core.crypto.*;
import net.corda.core.identity.*;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.*;
import net.corda.core.node.services.vault.*;
import net.corda.core.node.services.vault.QueryCriteria.*;
import net.corda.core.schemas.*;
import net.corda.core.serialization.*;
import net.corda.core.transactions.*;
import net.corda.node.services.database.*;
import net.corda.node.services.schema.*;
import net.corda.schemas.*;
import net.corda.testing.node.*;
import org.jetbrains.annotations.*;
import org.jetbrains.exposed.sql.*;
import org.junit.*;
import rx.Observable;

import java.io.*;
import java.lang.reflect.*;
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
    VaultService vaultSvc;
    private VaultQueryService vaultQuerySvc;
    private Closeable dataSource;
    private Database database;

    @Before
    public void setUp() {
        Properties dataSourceProps = makeTestDataSourceProperties(SecureHash.randomSHA256().toString());
        Pair<Closeable, Database> dataSourceAndDatabase = configureDatabase(dataSourceProps);
        dataSource = dataSourceAndDatabase.getFirst();
        database = dataSourceAndDatabase.getSecond();

        Set<MappedSchema> customSchemas = new HashSet<>(Arrays.asList(DummyLinearStateSchemaV1.INSTANCE));
        HibernateConfiguration hibernateConfig = new HibernateConfiguration(new NodeSchemaService(customSchemas));
        transaction(database,
                    statement -> { services = new MockServices(getMEGA_CORP_KEY()) {
                        @NotNull
                        @Override
                        public VaultService getVaultService() {
                            return makeVaultService(dataSourceProps, hibernateConfig);
                        }

                        @Override
                        public VaultQueryService getVaultQueryService() {
                            return new HibernateVaultQueryImpl(hibernateConfig, getVaultService().getUpdatesPublisher());
                        }

                        @Override
                        public void recordTransactions(@NotNull Iterable<SignedTransaction> txs) {
                            for (SignedTransaction stx : txs) {
                                getValidatedTransactions().addTransaction(stx);
                            }

                            Stream<WireTransaction> wtxn = StreamSupport.stream(txs.spliterator(), false).map(SignedTransaction::getTx);
                            getVaultService().notifyAll(wtxn.collect(Collectors.toList()));
                        }
                    };
            vaultSvc = services.getVaultService();
            vaultQuerySvc = services.getVaultQueryService();

            return services;
        });
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
    public void unconsumedLinearStates() throws VaultQueryException {
        transaction(database, tx -> {

            fillWithSomeTestLinearStates(services, 3);

            // DOCSTART VaultJavaQueryExample0
            Vault.Page<LinearState> results = vaultQuerySvc.queryBy(LinearState.class);
            // DOCEND VaultJavaQueryExample0

            assertThat(results.getStates()).hasSize(3);

            return tx;
        });
    }

    @Test
    public void consumedCashStates() {
        transaction(database, tx -> {

            Amount<Currency> amount = new Amount<>(100, Currency.getInstance("USD"));

            fillWithSomeTestCash(services,
                                 new Amount<>(100, Currency.getInstance("USD")),
                                 getDUMMY_NOTARY(),
                                3,
                                3,
                                 new Random(),
                                 new OpaqueBytes("1".getBytes()),
                                null,
                                 getDUMMY_CASH_ISSUER(),
                                 getDUMMY_CASH_ISSUER_KEY() );

            consumeCash(services, amount);

            // DOCSTART VaultJavaQueryExample1
            VaultQueryCriteria criteria = new VaultQueryCriteria(Vault.StateStatus.CONSUMED);
            Vault.Page<Cash.State> results = vaultQuerySvc.queryBy(Cash.State.class, criteria);
            // DOCEND VaultJavaQueryExample1

            assertThat(results.getStates()).hasSize(3);

            return tx;
        });
    }

    @Test
    public void consumedDealStatesPagedSorted() throws VaultQueryException {
        transaction(database, tx -> {

            Vault<LinearState> states = fillWithSomeTestLinearStates(services, 10, null);
            StateAndRef<LinearState> linearState = states.getStates().iterator().next();
            UniqueIdentifier uid = linearState.component1().getData().getLinearId();

            List<String> dealIds = Arrays.asList("123", "456", "789");
            Vault<DealState> dealStates = fillWithSomeTestDeals(services, dealIds);

            // consume states
            consumeDeals(services, (List<? extends StateAndRef<? extends DealState>>) dealStates.getStates());
            consumeLinearStates(services, Arrays.asList(linearState));

            // DOCSTART VaultJavaQueryExample2
            Vault.StateStatus status = Vault.StateStatus.CONSUMED;
            @SuppressWarnings("unchecked")
            Set<Class<LinearState>> contractStateTypes = new HashSet(Collections.singletonList(LinearState.class));

            QueryCriteria vaultCriteria = new VaultQueryCriteria(status, contractStateTypes);

            List<UniqueIdentifier> linearIds = Arrays.asList(uid);
            QueryCriteria linearCriteriaAll = new LinearStateQueryCriteria(null, linearIds);
            QueryCriteria dealCriteriaAll = new LinearStateQueryCriteria(null, null, dealIds);

            QueryCriteria compositeCriteria1 = or(dealCriteriaAll, linearCriteriaAll);
            QueryCriteria compositeCriteria2 = and(vaultCriteria, compositeCriteria1);

            PageSpecification pageSpec  = new PageSpecification(0, getMAX_PAGE_SIZE());
            Sort.SortColumn sortByUid = new Sort.SortColumn(new SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC);
            Sort sorting = new Sort(ImmutableSet.of(sortByUid));
            Vault.Page<LinearState> results = vaultQuerySvc.queryBy(LinearState.class, compositeCriteria2, pageSpec, sorting);
            // DOCEND VaultJavaQueryExample2

            assertThat(results.getStates()).hasSize(4);

            return tx;
        });
    }

    @Test
    public void customQueryForCashStatesWithAmountOfCurrencyGreaterOrEqualThanQuantity() {
        transaction(database, tx -> {

            Amount<Currency> pounds = new Amount<>(100, Currency.getInstance("GBP"));
            Amount<Currency> dollars100 = new Amount<>(100, Currency.getInstance("USD"));
            Amount<Currency> dollars10 = new Amount<>(10, Currency.getInstance("USD"));
            Amount<Currency> dollars1 = new Amount<>(1, Currency.getInstance("USD"));

            fillWithSomeTestCash(services, pounds, getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER(), getDUMMY_CASH_ISSUER_KEY());
            fillWithSomeTestCash(services, dollars100, getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER(), getDUMMY_CASH_ISSUER_KEY());
            fillWithSomeTestCash(services, dollars10, getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER(), getDUMMY_CASH_ISSUER_KEY());
            fillWithSomeTestCash(services, dollars1, getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER(), getDUMMY_CASH_ISSUER_KEY());

            try {
                // DOCSTART VaultJavaQueryExample3
                QueryCriteria generalCriteria = new VaultQueryCriteria(Vault.StateStatus.ALL);

                Field attributeCurrency = CashSchemaV1.PersistentCashState.class.getDeclaredField("currency");
                Field attributeQuantity = CashSchemaV1.PersistentCashState.class.getDeclaredField("pennies");

                CriteriaExpression currencyIndex = Builder.INSTANCE.equal(attributeCurrency, "USD");
                CriteriaExpression quantityIndex = Builder.INSTANCE.greaterThanOrEqual(attributeQuantity, 10L);

                QueryCriteria customCriteria2 = new VaultCustomQueryCriteria(quantityIndex);
                QueryCriteria customCriteria1 = new VaultCustomQueryCriteria(currencyIndex);


                QueryCriteria criteria = QueryCriteriaKt.and(QueryCriteriaKt.and(generalCriteria, customCriteria1), customCriteria2);
                Vault.Page<ContractState> results = vaultQuerySvc.queryBy(Cash.State.class, criteria);
                // DOCEND VaultJavaQueryExample3

                assertThat(results.getStates()).hasSize(2);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
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
                    new Amount<>(100, Currency.getInstance("USD")),
                    getDUMMY_NOTARY(),
                    3,
                    3,
                    new Random(),
                    new OpaqueBytes("1".getBytes()),
                    null,
                    getDUMMY_CASH_ISSUER(),
                    getDUMMY_CASH_ISSUER_KEY() );

            // DOCSTART VaultJavaQueryExample4
            @SuppressWarnings("unchecked")
            Set<Class<ContractState>> contractStateTypes = new HashSet(Collections.singletonList(Cash.State.class));

            VaultQueryCriteria criteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, contractStateTypes);
            DataFeed<Vault.Page<ContractState>, Vault.Update> results = vaultQuerySvc.trackBy(ContractState.class, criteria);

            Vault.Page<ContractState> snapshot = results.getCurrent();
            Observable<Vault.Update> updates = results.getFuture();

            // DOCEND VaultJavaQueryExample4
            assertThat(snapshot.getStates()).hasSize(3);

            return tx;
        });
    }

    @Test
    public void trackDealStatesPagedSorted() {
        transaction(database, tx -> {

            Vault<LinearState> states = fillWithSomeTestLinearStates(services, 10, null);
            UniqueIdentifier uid = states.getStates().iterator().next().component1().getData().getLinearId();

            List<String> dealIds = Arrays.asList("123", "456", "789");
            fillWithSomeTestDeals(services, dealIds);

            // DOCSTART VaultJavaQueryExample5
            @SuppressWarnings("unchecked")
            Set<Class<ContractState>> contractStateTypes = new HashSet(Arrays.asList(DealState.class, LinearState.class));
            QueryCriteria vaultCriteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, contractStateTypes);

            List<UniqueIdentifier> linearIds = Arrays.asList(uid);
            List<AbstractParty> dealParty = Arrays.asList(getMEGA_CORP());
            QueryCriteria dealCriteria = new LinearStateQueryCriteria(dealParty, null, dealIds);

            QueryCriteria linearCriteria = new LinearStateQueryCriteria(dealParty, linearIds, null);


            QueryCriteria dealOrLinearIdCriteria = or(dealCriteria, linearCriteria);

            QueryCriteria compositeCriteria = and(dealOrLinearIdCriteria, vaultCriteria);

            PageSpecification pageSpec  = new PageSpecification(0, getMAX_PAGE_SIZE());
            Sort.SortColumn sortByUid = new Sort.SortColumn(new SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC);
            Sort sorting = new Sort(ImmutableSet.of(sortByUid));
            DataFeed<Vault.Page<ContractState>, Vault.Update> results = vaultQuerySvc.trackBy(ContractState.class, compositeCriteria, pageSpec, sorting);

            Vault.Page<ContractState> snapshot = results.getCurrent();
            Observable<Vault.Update> updates = results.getFuture();
            // DOCEND VaultJavaQueryExample5

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
            Amount<Currency> amount = new Amount<>(100, Currency.getInstance("USD"));
            fillWithSomeTestCash(services,
                    new Amount<>(100, Currency.getInstance("USD")),
                    getDUMMY_NOTARY(),
                    3,
                    3,
                    new Random(),
                    new OpaqueBytes("1".getBytes()),
                    null,
                    getDUMMY_CASH_ISSUER(),
                    getDUMMY_CASH_ISSUER_KEY() );

            consumeCash(services, amount);

            // DOCSTART VaultDeprecatedJavaQueryExample1
            @SuppressWarnings("unchecked")
            Set<Class<ContractState>> contractStateTypes = new HashSet(Collections.singletonList(Cash.State.class));
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

            Vault<LinearState> linearStates = fillWithSomeTestLinearStates(services, 4,null);
            UniqueIdentifier trackUid = linearStates.getStates().iterator().next().component1().getData().getLinearId();

            consumeLinearStates(services, (List<? extends StateAndRef<? extends LinearState>>) linearStates.getStates());

            // DOCSTART VaultDeprecatedJavaQueryExample0
            @SuppressWarnings("unchecked")
            Set<Class<LinearState>> contractStateTypes = new HashSet(Collections.singletonList(DummyLinearContract.State.class));
            EnumSet<Vault.StateStatus> status = EnumSet.of(Vault.StateStatus.CONSUMED);

            // WARNING! unfortunately cannot use inlined reified Kotlin extension methods.
            Iterable<StateAndRef<LinearState>> results = vaultSvc.states(contractStateTypes, status, true);
            // DOCEND VaultDeprecatedJavaQueryExample0

            assertThat(results).hasSize(4);

            return tx;
        });
    }
}
