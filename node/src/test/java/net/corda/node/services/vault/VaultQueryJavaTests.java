package net.corda.node.services.vault;

import com.google.common.collect.ImmutableSet;
import kotlin.Pair;
import net.corda.contracts.DealState;
import net.corda.contracts.asset.Cash;
import net.corda.core.contracts.*;
import net.corda.core.contracts.testing.DummyLinearContract;
import net.corda.core.crypto.SecureHash;
import net.corda.core.identity.AbstractParty;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.VaultQueryException;
import net.corda.core.node.services.VaultQueryService;
import net.corda.core.node.services.VaultService;
import net.corda.core.node.services.vault.*;
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria;
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria;
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.testing.DummyLinearStateSchemaV1;
import net.corda.core.serialization.OpaqueBytes;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.WireTransaction;
import net.corda.node.services.database.HibernateConfiguration;
import net.corda.node.services.schema.NodeSchemaService;
import net.corda.schemas.CashSchemaV1;
import net.corda.testing.TestConstants;
import net.corda.testing.contracts.VaultFiller;
import net.corda.testing.node.MockServices;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.exposed.sql.Database;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.corda.contracts.asset.CashKt.getDUMMY_CASH_ISSUER;
import static net.corda.contracts.asset.CashKt.getDUMMY_CASH_ISSUER_KEY;
import static net.corda.core.contracts.ContractsDSL.USD;
import static net.corda.core.node.services.vault.QueryCriteriaKt.and;
import static net.corda.core.node.services.vault.QueryCriteriaKt.or;
import static net.corda.core.node.services.vault.QueryCriteriaUtilsKt.getMAX_PAGE_SIZE;
import static net.corda.node.utilities.DatabaseSupportKt.configureDatabase;
import static net.corda.node.utilities.DatabaseSupportKt.transaction;
import static net.corda.testing.CoreTestUtils.getMEGA_CORP;
import static net.corda.testing.CoreTestUtils.getMEGA_CORP_KEY;
import static net.corda.testing.node.MockServicesKt.makeTestDataSourceProperties;
import static org.assertj.core.api.Assertions.assertThat;

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

        Set<MappedSchema> customSchemas = new HashSet<>(Collections.singletonList(DummyLinearStateSchemaV1.INSTANCE));
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

            VaultFiller.fillWithSomeTestLinearStates(services, 3);

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

            VaultFiller.fillWithSomeTestCash(services,
                                 new Amount<>(100, Currency.getInstance("USD")),
                                 TestConstants.getDUMMY_NOTARY(),
                                3,
                                3,
                                 new Random(),
                                 new OpaqueBytes("1".getBytes()),
                                null,
                                 getDUMMY_CASH_ISSUER(),
                                 getDUMMY_CASH_ISSUER_KEY() );

            VaultFiller.consumeCash(services, amount);

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

            Vault<LinearState> states = VaultFiller.fillWithSomeTestLinearStates(services, 10, null);
            StateAndRef<LinearState> linearState = states.getStates().iterator().next();
            UniqueIdentifier uid = linearState.component1().getData().getLinearId();

            List<String> dealIds = Arrays.asList("123", "456", "789");
            Vault<DealState> dealStates = VaultFiller.fillWithSomeTestDeals(services, dealIds);

            // consume states
            VaultFiller.consumeDeals(services, (List<? extends StateAndRef<? extends DealState>>) dealStates.getStates());
            VaultFiller.consumeLinearStates(services, Collections.singletonList(linearState));

            // DOCSTART VaultJavaQueryExample2
            Vault.StateStatus status = Vault.StateStatus.CONSUMED;
            @SuppressWarnings("unchecked")
            Set<Class<LinearState>> contractStateTypes = new HashSet(Collections.singletonList(LinearState.class));

            QueryCriteria vaultCriteria = new VaultQueryCriteria(status, contractStateTypes);

            List<UniqueIdentifier> linearIds = Collections.singletonList(uid);
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

            VaultFiller.fillWithSomeTestCash(services, pounds, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER(), getDUMMY_CASH_ISSUER_KEY());
            VaultFiller.fillWithSomeTestCash(services, dollars100, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER(), getDUMMY_CASH_ISSUER_KEY());
            VaultFiller.fillWithSomeTestCash(services, dollars10, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER(), getDUMMY_CASH_ISSUER_KEY());
            VaultFiller.fillWithSomeTestCash(services, dollars1, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER(), getDUMMY_CASH_ISSUER_KEY());

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
            VaultFiller.fillWithSomeTestCash(services,
                    new Amount<>(100, Currency.getInstance("USD")),
                    TestConstants.getDUMMY_NOTARY(),
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

            Vault<LinearState> states = VaultFiller.fillWithSomeTestLinearStates(services, 10, null);
            UniqueIdentifier uid = states.getStates().iterator().next().component1().getData().getLinearId();

            List<String> dealIds = Arrays.asList("123", "456", "789");
            VaultFiller.fillWithSomeTestDeals(services, dealIds);

            // DOCSTART VaultJavaQueryExample5
            @SuppressWarnings("unchecked")
            Set<Class<ContractState>> contractStateTypes = new HashSet(Arrays.asList(DealState.class, LinearState.class));
            QueryCriteria vaultCriteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, contractStateTypes);

            List<UniqueIdentifier> linearIds = Collections.singletonList(uid);
            List<AbstractParty> dealParty = Collections.singletonList(getMEGA_CORP());
            QueryCriteria dealCriteria = new LinearStateQueryCriteria(dealParty, null, dealIds);
            QueryCriteria linearCriteria = new LinearStateQueryCriteria(dealParty, linearIds, null);
            QueryCriteria dealOrLinearIdCriteria = or(dealCriteria, linearCriteria);
            QueryCriteria compositeCriteria = and(dealOrLinearIdCriteria, vaultCriteria);

            PageSpecification pageSpec  = new PageSpecification(0, getMAX_PAGE_SIZE());
            Sort.SortColumn sortByUid = new Sort.SortColumn(new SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC);
            Sort sorting = new Sort(ImmutableSet.of(sortByUid));
            DataFeed<Vault.Page<ContractState>, Vault.Update> results = vaultQuerySvc.trackBy(ContractState.class, compositeCriteria, pageSpec, sorting);

            Vault.Page<ContractState> snapshot = results.getSnapshot();
            Observable<Vault.Update> updates = results.getUpdates();
            // DOCEND VaultJavaQueryExample5

            assertThat(snapshot.getStates()).hasSize(13);

            return tx;
        });
    }

    /**
     * Deprecated usage
     */

    @Test
    public void consumedStatesDeprecated() {
        transaction(database, tx -> {
            Amount<Currency> amount = new Amount<>(100, USD);
            VaultFiller.fillWithSomeTestCash(services,
                    new Amount<>(100, USD),
                    TestConstants.getDUMMY_NOTARY(),
                    3,
                    3,
                    new Random(),
                    new OpaqueBytes("1".getBytes()),
                    null,
                    getDUMMY_CASH_ISSUER(),
                    getDUMMY_CASH_ISSUER_KEY() );

            VaultFiller.consumeCash(services, amount);

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

            Vault<LinearState> linearStates = VaultFiller.fillWithSomeTestLinearStates(services, 4,null);
            linearStates.getStates().iterator().next().component1().getData().getLinearId();

            VaultFiller.consumeLinearStates(services, (List<? extends StateAndRef<? extends LinearState>>) linearStates.getStates());

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
