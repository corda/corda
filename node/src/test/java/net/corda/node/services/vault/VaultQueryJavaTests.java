package net.corda.node.services.vault;

import com.google.common.collect.ImmutableSet;
import kotlin.*;
import net.corda.contracts.DealState;
import net.corda.contracts.asset.Cash;
import net.corda.contracts.asset.CashUtilities;
import net.corda.core.contracts.*;
import net.corda.core.crypto.EncodingUtils;
import net.corda.core.identity.AbstractParty;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.IdentityService;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.VaultQueryException;
import net.corda.core.node.services.VaultQueryService;
import net.corda.core.node.services.vault.*;
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria;
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria;
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.node.utilities.*;
import net.corda.schemas.CashSchemaV1;
import net.corda.testing.TestConstants;
import net.corda.testing.TestDependencyInjectionBase;
import net.corda.testing.contracts.DummyLinearContract;
import net.corda.testing.contracts.VaultFiller;
import net.corda.testing.node.MockServices;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.corda.contracts.asset.CashUtilities.getDUMMY_CASH_ISSUER;
import static net.corda.contracts.asset.CashUtilities.getDUMMY_CASH_ISSUER_KEY;
import static net.corda.core.node.services.vault.QueryCriteriaUtils.DEFAULT_PAGE_NUM;
import static net.corda.core.node.services.vault.QueryCriteriaUtils.MAX_PAGE_SIZE;
import static net.corda.core.utilities.ByteArrays.toHexString;
import static net.corda.testing.CoreTestUtils.*;
import static net.corda.testing.TestConstants.getDUMMY_NOTARY;
import static net.corda.testing.TestConstants.getDUMMY_NOTARY_KEY;
import static net.corda.testing.node.MockServicesKt.makeTestDatabaseAndMockServices;
import static net.corda.testing.node.MockServicesKt.makeTestIdentityService;
import static org.assertj.core.api.Assertions.assertThat;

public class VaultQueryJavaTests extends TestDependencyInjectionBase {

    private MockServices services;
    private MockServices issuerServices;
    private VaultQueryService vaultQuerySvc;
    private CordaPersistence database;

    @Before
    public void setUp() {
        ArrayList<KeyPair> keys = new ArrayList<>();
        keys.add(getMEGA_CORP_KEY());
        keys.add(getDUMMY_NOTARY_KEY());
        Set<MappedSchema> requiredSchemas = new HashSet<>();
        requiredSchemas.add(CashSchemaV1.INSTANCE);
        IdentityService identitySvc = makeTestIdentityService();
        @SuppressWarnings("unchecked")
        Pair<CordaPersistence, MockServices> databaseAndServices = makeTestDatabaseAndMockServices(requiredSchemas, keys, () -> identitySvc);
        issuerServices = new MockServices(getDUMMY_CASH_ISSUER_KEY(), getBOC_KEY());
        database = databaseAndServices.getFirst();
        services = databaseAndServices.getSecond();
        vaultQuerySvc = services.getVaultQueryService();
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
            Vault.Page<LinearState> results = vaultQuerySvc.queryBy(LinearState.class);
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
            Vault.Page<DummyLinearContract.State> results = vaultQuerySvc.queryBy(DummyLinearContract.State.class, criteria, sorting);

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
                    new OpaqueBytes("1".getBytes()),
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
            Vault.Page<Cash.State> results = vaultQuerySvc.queryBy(Cash.State.class, criteria);
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
                return new Triple(linearState,uid,dealStates);
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

            List<UUID> linearIds = Collections.singletonList(ids.getSecond().getId());
            QueryCriteria linearCriteriaAll = new LinearStateQueryCriteria(null, linearIds);
            QueryCriteria dealCriteriaAll = new LinearStateQueryCriteria(null, null, dealIds);

            QueryCriteria compositeCriteria1 = dealCriteriaAll.or(linearCriteriaAll);
            QueryCriteria compositeCriteria2 = vaultCriteria.and(compositeCriteria1);

            PageSpecification pageSpec = new PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE);
            Sort.SortColumn sortByUid = new Sort.SortColumn(new SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC);
            Sort sorting = new Sort(ImmutableSet.of(sortByUid));
            Vault.Page<LinearState> results = vaultQuerySvc.queryBy(LinearState.class, compositeCriteria2, pageSpec, sorting);
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

            VaultFiller.fillWithSomeTestCash(services, pounds, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars100, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars10, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars1, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
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
                    new OpaqueBytes("1".getBytes()),
                    null,
                    getDUMMY_CASH_ISSUER());
            return tx;
        });
        database.transaction(tx -> {
            // DOCSTART VaultJavaQueryExample4
            @SuppressWarnings("unchecked")
            Set<Class<ContractState>> contractStateTypes = new HashSet(Collections.singletonList(Cash.State.class));

            VaultQueryCriteria criteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, contractStateTypes);
            DataFeed<Vault.Page<ContractState>, Vault.Update<ContractState>> results = vaultQuerySvc.trackBy(ContractState.class, criteria);

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

            List<UUID> linearIds = Collections.singletonList(uid.getId());
            List<AbstractParty> dealParty = Collections.singletonList(getMEGA_CORP());
            QueryCriteria dealCriteria = new LinearStateQueryCriteria(dealParty, null, dealIds);
            QueryCriteria linearCriteria = new LinearStateQueryCriteria(dealParty, linearIds, null);
            QueryCriteria dealOrLinearIdCriteria = dealCriteria.or(linearCriteria);
            QueryCriteria compositeCriteria = dealOrLinearIdCriteria.and(vaultCriteria);

            PageSpecification pageSpec = new PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE);
            Sort.SortColumn sortByUid = new Sort.SortColumn(new SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC);
            Sort sorting = new Sort(ImmutableSet.of(sortByUid));
            DataFeed<Vault.Page<ContractState>, Vault.Update<ContractState>> results = vaultQuerySvc.trackBy(ContractState.class, compositeCriteria, pageSpec, sorting);

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

            VaultFiller.fillWithSomeTestCash(services, dollars100, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars200, issuerServices, TestConstants.getDUMMY_NOTARY(), 2, 2, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars300, issuerServices, TestConstants.getDUMMY_NOTARY(), 3, 3, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, pounds, issuerServices, TestConstants.getDUMMY_NOTARY(), 4, 4, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, swissfrancs, issuerServices, TestConstants.getDUMMY_NOTARY(), 5, 5, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());

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
                Vault.Page<Cash.State> results = vaultQuerySvc.queryBy(Cash.State.class, criteria);
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

            VaultFiller.fillWithSomeTestCash(services, dollars100, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars200, issuerServices, TestConstants.getDUMMY_NOTARY(), 2, 2, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars300, issuerServices, TestConstants.getDUMMY_NOTARY(), 3, 3, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, pounds, issuerServices, TestConstants.getDUMMY_NOTARY(), 4, 4, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, swissfrancs, issuerServices, TestConstants.getDUMMY_NOTARY(), 5, 5, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());

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
                Vault.Page<Cash.State> results = vaultQuerySvc.queryBy(Cash.State.class, criteria);
                // DOCEND VaultJavaQueryExample22

                assertThat(results.getOtherResults()).hasSize(27);
                /** CHF */
                assertThat(results.getOtherResults().get(0)).isEqualTo(500L);
                assertThat(results.getOtherResults().get(1)).isEqualTo("CHF");
                assertThat(results.getOtherResults().get(2)).isEqualTo(5L);
                assertThat(results.getOtherResults().get(3)).isEqualTo(102L);
                assertThat(results.getOtherResults().get(4)).isEqualTo("CHF");
                assertThat(results.getOtherResults().get(5)).isEqualTo(94L);
                assertThat(results.getOtherResults().get(6)).isEqualTo("CHF");
                assertThat(results.getOtherResults().get(7)).isEqualTo(100.00);
                assertThat(results.getOtherResults().get(8)).isEqualTo("CHF");
                /** GBP */
                assertThat(results.getOtherResults().get(9)).isEqualTo(400L);
                assertThat(results.getOtherResults().get(10)).isEqualTo("GBP");
                assertThat(results.getOtherResults().get(11)).isEqualTo(4L);
                assertThat(results.getOtherResults().get(12)).isEqualTo(103L);
                assertThat(results.getOtherResults().get(13)).isEqualTo("GBP");
                assertThat(results.getOtherResults().get(14)).isEqualTo(93L);
                assertThat(results.getOtherResults().get(15)).isEqualTo("GBP");
                assertThat(results.getOtherResults().get(16)).isEqualTo(100.0);
                assertThat(results.getOtherResults().get(17)).isEqualTo("GBP");
                /** USD */
                assertThat(results.getOtherResults().get(18)).isEqualTo(600L);
                assertThat(results.getOtherResults().get(19)).isEqualTo("USD");
                assertThat(results.getOtherResults().get(20)).isEqualTo(6L);
                assertThat(results.getOtherResults().get(21)).isEqualTo(113L);
                assertThat(results.getOtherResults().get(22)).isEqualTo("USD");
                assertThat(results.getOtherResults().get(23)).isEqualTo(87L);
                assertThat(results.getOtherResults().get(24)).isEqualTo("USD");
                assertThat(results.getOtherResults().get(25)).isEqualTo(100.0);
                assertThat(results.getOtherResults().get(26)).isEqualTo("USD");

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

            VaultFiller.fillWithSomeTestCash(services, dollars100, issuerServices, TestConstants.getDUMMY_NOTARY(), 1, 1, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, dollars200, issuerServices, TestConstants.getDUMMY_NOTARY(), 2, 2, new Random(0L), new OpaqueBytes("1".getBytes()), null, getBOC().ref(new OpaqueBytes("1".getBytes())));
            VaultFiller.fillWithSomeTestCash(services, pounds300, issuerServices, TestConstants.getDUMMY_NOTARY(), 3, 3, new Random(0L), new OpaqueBytes("1".getBytes()), null, getDUMMY_CASH_ISSUER());
            VaultFiller.fillWithSomeTestCash(services, pounds400, issuerServices, TestConstants.getDUMMY_NOTARY(), 4, 4, new Random(0L), new OpaqueBytes("1".getBytes()), null, getBOC().ref(new OpaqueBytes("1".getBytes())));

            return tx;
        });
        database.transaction(tx -> {
            try {
                // DOCSTART VaultJavaQueryExample23
                Field pennies = CashSchemaV1.PersistentCashState.class.getDeclaredField("pennies");
                Field currency = CashSchemaV1.PersistentCashState.class.getDeclaredField("currency");
                Field issuerParty = CashSchemaV1.PersistentCashState.class.getDeclaredField("issuerParty");

                QueryCriteria sumCriteria = new VaultCustomQueryCriteria(Builder.sum(pennies, Arrays.asList(issuerParty, currency), Sort.Direction.DESC));

                Vault.Page<Cash.State> results = vaultQuerySvc.queryBy(Cash.State.class, sumCriteria);
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
