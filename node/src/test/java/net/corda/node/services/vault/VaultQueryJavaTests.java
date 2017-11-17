package net.corda.node.services.vault;

import com.google.common.collect.*;
import kotlin.*;
import net.corda.core.contracts.*;
import net.corda.core.identity.*;
import net.corda.core.messaging.*;
import net.corda.core.node.services.*;
import net.corda.core.node.services.vault.*;
import net.corda.core.node.services.vault.QueryCriteria.*;
import net.corda.core.utilities.*;
import net.corda.finance.contracts.*;
import net.corda.finance.contracts.asset.*;
import net.corda.finance.schemas.*;
import net.corda.node.utilities.*;
import net.corda.testing.*;
import net.corda.testing.contracts.*;
import net.corda.testing.node.*;
import org.junit.*;
import rx.Observable;

import java.io.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;
import java.util.stream.*;

import static net.corda.core.node.services.vault.QueryCriteriaUtils.*;
import static net.corda.core.utilities.ByteArrays.*;
import static net.corda.finance.contracts.asset.CashUtilities.*;
import static net.corda.testing.CoreTestUtils.*;
import static net.corda.testing.TestConstants.*;
import static net.corda.testing.node.MockServices.*;
import static org.assertj.core.api.Assertions.*;

public class VaultQueryJavaTests {
    @Rule
    public final SerializationEnvironmentRule testSerialization = new SerializationEnvironmentRule();
    private MockServices services;
    private MockServices issuerServices;
    private VaultService vaultService;
    private CordaPersistence database;

    @Before
    public void setUp() {
        List<String> cordappPackages = Arrays.asList("net.corda.testing.contracts", "net.corda.finance.contracts.asset", CashSchemaV1.class.getPackage().getName());
        ArrayList<KeyPair> keys = new ArrayList<>();
        keys.add(getMEGA_CORP_KEY());
        keys.add(getDUMMY_NOTARY_KEY());
        IdentityService identitySvc = makeTestIdentityService();
        @SuppressWarnings("unchecked")
        Pair<CordaPersistence, MockServices> databaseAndServices = makeTestDatabaseAndMockServices(keys, () -> identitySvc, cordappPackages);
        issuerServices = new MockServices(cordappPackages, getDUMMY_CASH_ISSUER_NAME(), getDUMMY_CASH_ISSUER_KEY(), getBOC_KEY());
        database = databaseAndServices.getFirst();
        services = databaseAndServices.getSecond();
        vaultService = services.getVaultService();
    }

    @After
    public void cleanUp() throws IOException {
        database.close();
    }

    /**
     * Sample Vault Query API tests
     */

    /**
     * Static queryBy() tests
     */

    @Test
    public void unconsumedLinearStates() throws VaultQueryException {
        database.transaction(tx -> {
            VaultFiller.fillWithSomeTestLinearStates(services, 3);
            return tx;
        });
        database.transaction(tx -> {
            // DOCSTART VaultJavaQueryExample0
            Vault.Page<LinearState> results = vaultService.queryBy(LinearState.class);
            // DOCEND VaultJavaQueryExample0

            assertThat(results.getStates()).hasSize(3);

            return tx;
        });
    }

    @Test
    public void unconsumedStatesForStateRefsSortedByTxnId() {
        Vault<LinearState> issuedStates =
                database.transaction(tx -> {
                    VaultFiller.fillWithSomeTestLinearStates(services, 8);
                    return VaultFiller.fillWithSomeTestLinearStates(services, 2);
                });
        database.transaction(tx -> {
            Stream<StateRef> stateRefsStream = StreamSupport.stream(issuedStates.getStates().spliterator(), false).map(StateAndRef::getRef);
            List<StateRef> stateRefs = stateRefsStream.collect(Collectors.toList());

            SortAttribute.Standard sortAttribute = new SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF_TXN_ID);
            Sort sorting = new Sort(Collections.singletonList(new Sort.SortColumn(sortAttribute, Sort.Direction.ASC)));
            VaultQueryCriteria criteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, null, stateRefs);
            Vault.Page<DummyLinearContract.State> results = vaultService.queryBy(DummyLinearContract.State.class, criteria, sorting);

            assertThat(results.getStates()).hasSize(2);

            stateRefs.sort(Comparator.comparing(stateRef -> toHexString(stateRef.getTxhash().getBytes())));
            assertThat(results.getStates().get(0).getRef()).isEqualTo(stateRefs.get(0));
            assertThat(results.getStates().get(1).getRef()).isEqualTo(stateRefs.get(1));

            return tx;
        });
    }

    @Test
    public void consumedCashStates() {
        Amount<Currency> amount = new Amount<>(100, Currency.getInstance("USD"));
        database.transaction(tx -> {
            VaultFiller.fillWithSomeTestCash(services,
                    new Amount<Currency>(100, Currency.getInstance("USD")),
                    issuerServices,
                    TestConstants.getDUMMY_NOTARY(),
                    3,
                    3,
                    new Random(),
                    null,
                    CashUtilities.getDUMMY_CASH_ISSUER());
            return tx;
        });
        database.transaction(tx -> {
            VaultFiller.consumeCash(services, amount, getDUMMY_NOTARY());
            return tx;
        });
        database.transaction(tx -> {
            // DOCSTART VaultJavaQueryExample1
            VaultQueryCriteria criteria = new VaultQueryCriteria(Vault.StateStatus.CONSUMED);
            Vault.Page<Cash.State> results = vaultService.queryBy(Cash.State.class, criteria);
            // DOCEND VaultJavaQueryExample1

            assertThat(results.getStates()).hasSize(3);

            return tx;
        });
    }

    @Test
    public void consumedDealStatesPagedSorted() throws VaultQueryException {
        List<String> dealIds = Arrays.asList("123", "456", "789");
        @SuppressWarnings("unchecked")
        Triple<StateAndRef<LinearState>, UniqueIdentifier, Vault<DealState>> ids =
                database.transaction((DatabaseTransaction tx) -> {
                    Vault<LinearState> states = VaultFiller.fillWithSomeTestLinearStates(services, 10, null);
                    StateAndRef<LinearState> linearState = states.getStates().iterator().next();
                    UniqueIdentifier uid = linearState.component1().getData().getLinearId();

                    Vault<DealState> dealStates = VaultFiller.fillWithSomeTestDeals(services, dealIds);
                    return new Triple(linearState, uid, dealStates);
                });
        database.transaction(tx -> {
            // consume states
            VaultFiller.consumeDeals(services, (List<? extends StateAndRef<? extends DealState>>) ids.getThird().getStates(), getDUMMY_NOTARY());
            VaultFiller.consumeLinearStates(services, Collections.singletonList(ids.getFirst()), getDUMMY_NOTARY());
            return tx;
        });
        database.transaction(tx -> {
            // DOCSTART VaultJavaQueryExample2
            Vault.StateStatus status = Vault.StateStatus.CONSUMED;
            @SuppressWarnings("unchecked")
            Set<Class<LinearState>> contractStateTypes = new HashSet(Collections.singletonList(LinearState.class));

            QueryCriteria vaultCriteria = new VaultQueryCriteria(status, contractStateTypes);

            List<UniqueIdentifier> linearIds = Collections.singletonList(ids.getSecond());
            QueryCriteria linearCriteriaAll = new LinearStateQueryCriteria(null, linearIds, Vault.StateStatus.UNCONSUMED, null);
            QueryCriteria dealCriteriaAll = new LinearStateQueryCriteria(null, null, dealIds);

            QueryCriteria compositeCriteria1 = dealCriteriaAll.or(linearCriteriaAll);
            QueryCriteria compositeCriteria2 = compositeCriteria1.and(vaultCriteria);

            PageSpecification pageSpec = new PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE);
            Sort.SortColumn sortByUid = new Sort.SortColumn(new SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC);
            Sort sorting = new Sort(ImmutableSet.of(sortByUid));
            Vault.Page<LinearState> results = vaultService.queryBy(LinearState.class, compositeCriteria2, pageSpec, sorting);
            // DOCEND VaultJavaQueryExample2

            assertThat(results.getStates()).hasSize(4);

            return tx;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public void customQueryForCashStatesWithAmountOfCurrencyGreaterOrEqualThanQuantity() {
        database.transaction(tx -> {

            Amount<Currency> pounds = new Amount<>(100, Currency.getInstance("GBP"));
            Amount<Currency> dollars100 = new Amount<>(100, Currency.getInstance("USD"));
            Amount<Currency> dollars10 = new Amount<>(10, Currency.getInstance("USD"));
            Amount<Currency> dollars1 = new Amount<>(1, Currency.getInstance("USD"));

            VaultFiller.fillWithSomeTestCash(services, pounds, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars100, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars10, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars1, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), null, getDUMMY_CASH_ISSUER());
            return tx;
        });
        database.transaction(tx -> {
            try {
                // DOCSTART VaultJavaQueryExample3
                QueryCriteria generalCriteria = new VaultQueryCriteria(Vault.StateStatus.ALL);

                Field attributeCurrency = CashSchemaV1.PersistentCashState.class.getDeclaredField("currency");
                Field attributeQuantity = CashSchemaV1.PersistentCashState.class.getDeclaredField("pennies");

                CriteriaExpression currencyIndex = Builder.equal(attributeCurrency, "USD");
                CriteriaExpression quantityIndex = Builder.greaterThanOrEqual(attributeQuantity, 10L);

                QueryCriteria customCriteria2 = new VaultCustomQueryCriteria(quantityIndex);
                QueryCriteria customCriteria1 = new VaultCustomQueryCriteria(currencyIndex);


                QueryCriteria criteria = generalCriteria.and(customCriteria1).and(customCriteria2);
                Vault.Page<ContractState> results = vaultService.queryBy(Cash.State.class, criteria);
                // DOCEND VaultJavaQueryExample3

                assertThat(results.getStates()).hasSize(2);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
            return tx;
        });
    }

    /**
     * Dynamic trackBy() tests
     */

    @Test
    public void trackCashStates() {
        database.transaction(tx -> {
            VaultFiller.fillWithSomeTestCash(services,
                    new Amount<>(100, Currency.getInstance("USD")),
                    issuerServices,
                    TestConstants.getDUMMY_NOTARY(),
                    3,
                    3,
                    new Random(),
                    null,
                    getDUMMY_CASH_ISSUER());
            return tx;
        });
        database.transaction(tx -> {
            // DOCSTART VaultJavaQueryExample4
            @SuppressWarnings("unchecked")
            Set<Class<ContractState>> contractStateTypes = new HashSet(Collections.singletonList(Cash.State.class));

            VaultQueryCriteria criteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, contractStateTypes);
            DataFeed<Vault.Page<ContractState>, Vault.Update<ContractState>> results = vaultService.trackBy(ContractState.class, criteria);

            Vault.Page<ContractState> snapshot = results.getSnapshot();
            Observable<Vault.Update<ContractState>> updates = results.getUpdates();

            // DOCEND VaultJavaQueryExample4
            assertThat(snapshot.getStates()).hasSize(3);

            return tx;
        });
    }

    @Test
    public void trackDealStatesPagedSorted() {
        List<String> dealIds = Arrays.asList("123", "456", "789");
        UniqueIdentifier uid =
                database.transaction(tx -> {
                    Vault<LinearState> states = VaultFiller.fillWithSomeTestLinearStates(services, 10, null);
                    UniqueIdentifier _uid = states.getStates().iterator().next().component1().getData().getLinearId();

                    VaultFiller.fillWithSomeTestDeals(services, dealIds);
                    return _uid;
                });
        database.transaction(tx -> {
            // DOCSTART VaultJavaQueryExample5
            @SuppressWarnings("unchecked")
            Set<Class<ContractState>> contractStateTypes = new HashSet(Arrays.asList(DealState.class, LinearState.class));
            QueryCriteria vaultCriteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, contractStateTypes);

            List<UniqueIdentifier> linearIds = Collections.singletonList(uid);
            List<AbstractParty> dealParty = Collections.singletonList(getMEGA_CORP());
            QueryCriteria dealCriteria = new LinearStateQueryCriteria(dealParty, null, dealIds);
            QueryCriteria linearCriteria = new LinearStateQueryCriteria(dealParty, linearIds, Vault.StateStatus.UNCONSUMED, null);
            QueryCriteria dealOrLinearIdCriteria = dealCriteria.or(linearCriteria);
            QueryCriteria compositeCriteria = dealOrLinearIdCriteria.and(vaultCriteria);

            PageSpecification pageSpec = new PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE);
            Sort.SortColumn sortByUid = new Sort.SortColumn(new SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC);
            Sort sorting = new Sort(ImmutableSet.of(sortByUid));
            DataFeed<Vault.Page<ContractState>, Vault.Update<ContractState>> results = vaultService.trackBy(ContractState.class, compositeCriteria, pageSpec, sorting);

            Vault.Page<ContractState> snapshot = results.getSnapshot();
            // DOCEND VaultJavaQueryExample5

            assertThat(snapshot.getStates()).hasSize(13);

            return tx;
        });
    }

    /**
     * Aggregation Functions
     */

    @Test
    @SuppressWarnings("unchecked")
    public void aggregateFunctionsWithoutGroupClause() {
        database.transaction(tx -> {

            Amount<Currency> dollars100 = new Amount<>(100, Currency.getInstance("USD"));
            Amount<Currency> dollars200 = new Amount<>(200, Currency.getInstance("USD"));
            Amount<Currency> dollars300 = new Amount<>(300, Currency.getInstance("USD"));
            Amount<Currency> pounds = new Amount<>(400, Currency.getInstance("GBP"));
            Amount<Currency> swissfrancs = new Amount<>(500, Currency.getInstance("CHF"));

            VaultFiller.fillWithSomeTestCash(services, dollars100, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars200, issuerServices, TestConstants.getDUMMY_NOTARY(), 2, 2, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars300, issuerServices, TestConstants.getDUMMY_NOTARY(), 3, 3, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, pounds, issuerServices, TestConstants.getDUMMY_NOTARY(), 4, 4, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, swissfrancs, issuerServices, TestConstants.getDUMMY_NOTARY(), 5, 5, new Random(0L), null, getDUMMY_CASH_ISSUER());

            return tx;
        });
        database.transaction(tx -> {
            try {
                // DOCSTART VaultJavaQueryExample21
                Field pennies = CashSchemaV1.PersistentCashState.class.getDeclaredField("pennies");

                QueryCriteria sumCriteria = new VaultCustomQueryCriteria(Builder.sum(pennies));
                QueryCriteria countCriteria = new VaultCustomQueryCriteria(Builder.count(pennies));
                QueryCriteria maxCriteria = new VaultCustomQueryCriteria(Builder.max(pennies));
                QueryCriteria minCriteria = new VaultCustomQueryCriteria(Builder.min(pennies));
                QueryCriteria avgCriteria = new VaultCustomQueryCriteria(Builder.avg(pennies));

                QueryCriteria criteria = sumCriteria.and(countCriteria).and(maxCriteria).and(minCriteria).and(avgCriteria);
                Vault.Page<Cash.State> results = vaultService.queryBy(Cash.State.class, criteria);
                // DOCEND VaultJavaQueryExample21

                assertThat(results.getOtherResults()).hasSize(5);
                assertThat(results.getOtherResults().get(0)).isEqualTo(1500L);
                assertThat(results.getOtherResults().get(1)).isEqualTo(15L);
                assertThat(results.getOtherResults().get(2)).isEqualTo(113L);
                assertThat(results.getOtherResults().get(3)).isEqualTo(87L);
                assertThat(results.getOtherResults().get(4)).isEqualTo(100.0);

            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
            return tx;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aggregateFunctionsWithSingleGroupClause() {
        database.transaction(tx -> {

            Amount<Currency> dollars100 = new Amount<>(100, Currency.getInstance("USD"));
            Amount<Currency> dollars200 = new Amount<>(200, Currency.getInstance("USD"));
            Amount<Currency> dollars300 = new Amount<>(300, Currency.getInstance("USD"));
            Amount<Currency> pounds = new Amount<>(400, Currency.getInstance("GBP"));
            Amount<Currency> swissfrancs = new Amount<>(500, Currency.getInstance("CHF"));

            VaultFiller.fillWithSomeTestCash(services, dollars100, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars200, issuerServices, TestConstants.getDUMMY_NOTARY(), 2, 2, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars300, issuerServices, TestConstants.getDUMMY_NOTARY(), 3, 3, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, pounds, issuerServices, TestConstants.getDUMMY_NOTARY(), 4, 4, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, swissfrancs, issuerServices, TestConstants.getDUMMY_NOTARY(), 5, 5, new Random(0L), null, getDUMMY_CASH_ISSUER());

            return tx;
        });
        database.transaction(tx -> {
            try {
                // DOCSTART VaultJavaQueryExample22
                Field pennies = CashSchemaV1.PersistentCashState.class.getDeclaredField("pennies");
                Field currency = CashSchemaV1.PersistentCashState.class.getDeclaredField("currency");

                QueryCriteria sumCriteria = new VaultCustomQueryCriteria(Builder.sum(pennies, Collections.singletonList(currency)));
                QueryCriteria countCriteria = new VaultCustomQueryCriteria(Builder.count(pennies));
                QueryCriteria maxCriteria = new VaultCustomQueryCriteria(Builder.max(pennies, Collections.singletonList(currency)));
                QueryCriteria minCriteria = new VaultCustomQueryCriteria(Builder.min(pennies, Collections.singletonList(currency)));
                QueryCriteria avgCriteria = new VaultCustomQueryCriteria(Builder.avg(pennies, Collections.singletonList(currency)));

                QueryCriteria criteria = sumCriteria.and(countCriteria).and(maxCriteria).and(minCriteria).and(avgCriteria);
                Vault.Page<Cash.State> results = vaultService.queryBy(Cash.State.class, criteria);
                // DOCEND VaultJavaQueryExample22

                assertThat(results.getOtherResults()).hasSize(18);
                /** CHF */
                assertThat(results.getOtherResults().get(0)).isEqualTo(500L);
                assertThat(results.getOtherResults().get(1)).isEqualTo(5L);
                assertThat(results.getOtherResults().get(2)).isEqualTo(102L);
                assertThat(results.getOtherResults().get(3)).isEqualTo(94L);
                assertThat(results.getOtherResults().get(4)).isEqualTo(100.00);
                assertThat(results.getOtherResults().get(5)).isEqualTo("CHF");
                /** GBP */
                assertThat(results.getOtherResults().get(6)).isEqualTo(400L);
                assertThat(results.getOtherResults().get(7)).isEqualTo(4L);
                assertThat(results.getOtherResults().get(8)).isEqualTo(103L);
                assertThat(results.getOtherResults().get(9)).isEqualTo(93L);
                assertThat(results.getOtherResults().get(10)).isEqualTo(100.0);
                assertThat(results.getOtherResults().get(11)).isEqualTo("GBP");
                /** USD */
                assertThat(results.getOtherResults().get(12)).isEqualTo(600L);
                assertThat(results.getOtherResults().get(13)).isEqualTo(6L);
                assertThat(results.getOtherResults().get(14)).isEqualTo(113L);
                assertThat(results.getOtherResults().get(15)).isEqualTo(87L);
                assertThat(results.getOtherResults().get(16)).isEqualTo(100.0);
                assertThat(results.getOtherResults().get(17)).isEqualTo("USD");

            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
            return tx;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aggregateFunctionsSumByIssuerAndCurrencyAndSortByAggregateSum() {
        database.transaction(tx -> {
            Amount<Currency> dollars100 = new Amount<>(100, Currency.getInstance("USD"));
            Amount<Currency> dollars200 = new Amount<>(200, Currency.getInstance("USD"));
            Amount<Currency> pounds300 = new Amount<>(300, Currency.getInstance("GBP"));
            Amount<Currency> pounds400 = new Amount<>(400, Currency.getInstance("GBP"));

            VaultFiller.fillWithSomeTestCash(services, dollars100, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars200, issuerServices, TestConstants.getDUMMY_NOTARY(), 2, 2, new Random(0L), null, getBOC().ref(new OpaqueBytes("1".getBytes())));
            VaultFiller.fillWithSomeTestCash(services, pounds300, issuerServices, TestConstants.getDUMMY_NOTARY(), 3, 3, new Random(0L), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, pounds400, issuerServices, TestConstants.getDUMMY_NOTARY(), 4, 4, new Random(0L), null, getBOC().ref(new OpaqueBytes("1".getBytes())));

            return tx;
        });
        database.transaction(tx -> {
            try {
                // DOCSTART VaultJavaQueryExample23
                Field pennies = CashSchemaV1.PersistentCashState.class.getDeclaredField("pennies");
                Field currency = CashSchemaV1.PersistentCashState.class.getDeclaredField("currency");
                Field issuerParty = CashSchemaV1.PersistentCashState.class.getDeclaredField("issuerParty");

                QueryCriteria sumCriteria = new VaultCustomQueryCriteria(Builder.sum(pennies, Arrays.asList(issuerParty, currency), Sort.Direction.DESC));

                Vault.Page<Cash.State> results = vaultService.queryBy(Cash.State.class, sumCriteria);
                // DOCEND VaultJavaQueryExample23

                assertThat(results.getOtherResults()).hasSize(12);

                assertThat(results.getOtherResults().get(0)).isEqualTo(400L);
                assertThat(results.getOtherResults().get(1)).isEqualTo(EncodingUtils.toBase58String(getBOC_PUBKEY()));
                assertThat(results.getOtherResults().get(2)).isEqualTo("GBP");
                assertThat(results.getOtherResults().get(3)).isEqualTo(300L);
                assertThat(results.getOtherResults().get(4)).isEqualTo(EncodingUtils.toBase58String(getDUMMY_CASH_ISSUER().getParty().getOwningKey()));
                assertThat(results.getOtherResults().get(5)).isEqualTo("GBP");
                assertThat(results.getOtherResults().get(6)).isEqualTo(200L);
                assertThat(results.getOtherResults().get(7)).isEqualTo(EncodingUtils.toBase58String(getBOC_PUBKEY()));
                assertThat(results.getOtherResults().get(8)).isEqualTo("USD");
                assertThat(results.getOtherResults().get(9)).isEqualTo(100L);
                assertThat(results.getOtherResults().get(10)).isEqualTo(EncodingUtils.toBase58String(getDUMMY_CASH_ISSUER().getParty().getOwningKey()));
                assertThat(results.getOtherResults().get(11)).isEqualTo("USD");

            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
            return tx;
        });
    }
}
