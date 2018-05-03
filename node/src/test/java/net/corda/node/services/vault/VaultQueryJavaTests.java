package net.corda.node.services.vault;

import com.google.common.collect.ImmutableSet;
import kotlin.Pair;
import kotlin.Triple;
import net.corda.core.contracts.*;
import net.corda.core.crypto.CryptoUtils;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.IdentityService;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.VaultQueryException;
import net.corda.core.node.services.VaultService;
import net.corda.core.node.services.vault.*;
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria;
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria;
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria;
import net.corda.finance.contracts.DealState;
import net.corda.finance.contracts.asset.Cash;
import net.corda.finance.schemas.CashSchemaV1;
import net.corda.finance.schemas.SampleCashSchemaV2;
import net.corda.node.services.api.IdentityServiceInternal;
import net.corda.nodeapi.internal.persistence.CordaPersistence;
import net.corda.nodeapi.internal.persistence.DatabaseTransaction;
import net.corda.testing.core.SerializationEnvironmentRule;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.internal.vault.DummyLinearContract;
import net.corda.testing.internal.vault.VaultFiller;
import net.corda.testing.node.MockServices;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import rx.Observable;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static net.corda.core.node.services.vault.Builder.sum;
import static net.corda.core.node.services.vault.QueryCriteriaUtils.*;
import static net.corda.core.utilities.ByteArrays.toHexString;
import static net.corda.testing.core.TestConstants.*;
import static net.corda.testing.internal.InternalTestUtilsKt.rigorousMock;
import static net.corda.testing.node.MockServices.makeTestDatabaseAndMockServices;
import static net.corda.testing.node.MockServicesKt.makeTestIdentityService;
import static org.assertj.core.api.Assertions.assertThat;

public class VaultQueryJavaTests {
    private static final TestIdentity BOC = new TestIdentity(BOC_NAME);
    private static final Party CHARLIE = new TestIdentity(CHARLIE_NAME, 90L).getParty();
    private static final TestIdentity DUMMY_CASH_ISSUER_INFO = new TestIdentity(new CordaX500Name("Snake Oil Issuer", "London", "GB"), 10L);
    private static final PartyAndReference DUMMY_CASH_ISSUER = DUMMY_CASH_ISSUER_INFO.ref((byte) 1);
    private static final TestIdentity DUMMY_NOTARY = new TestIdentity(DUMMY_NOTARY_NAME, 20L);
    private static final TestIdentity MEGA_CORP = new TestIdentity(new CordaX500Name("MegaCorp", "London", "GB"));
    @Rule
    public final SerializationEnvironmentRule testSerialization = new SerializationEnvironmentRule();
    private VaultFiller vaultFiller;
    private MockServices issuerServices;
    private VaultService vaultService;
    private CordaPersistence database;

    @Before
    public void setUp() throws CertificateException, InvalidAlgorithmParameterException {
        List<String> cordappPackages = asList("net.corda.testing.internal.vault", "net.corda.finance.contracts.asset", CashSchemaV1.class.getPackage().getName());
        IdentityService identitySvc = makeTestIdentityService(MEGA_CORP.getIdentity(), DUMMY_CASH_ISSUER_INFO.getIdentity(), DUMMY_NOTARY.getIdentity());
        Pair<CordaPersistence, MockServices> databaseAndServices = makeTestDatabaseAndMockServices(
                cordappPackages,
                identitySvc,
                MEGA_CORP,
                DUMMY_NOTARY.getKeyPair());
        issuerServices = new MockServices(cordappPackages, DUMMY_CASH_ISSUER_INFO, rigorousMock(IdentityServiceInternal.class), BOC.getKeyPair());
        database = databaseAndServices.getFirst();
        MockServices services = databaseAndServices.getSecond();
        vaultFiller = new VaultFiller(services, DUMMY_NOTARY);
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
    public void criteriaWithFieldFromMappedSuperclass() throws NoSuchFieldException {
        FieldInfo quantity = getField("quantity", SampleCashSchemaV2.PersistentCashState.class);
        FieldInfo currency = getField("currency", SampleCashSchemaV2.PersistentCashState.class);

        CriteriaExpression.AggregateFunctionExpression<Object, Boolean> expression = sum(quantity, singletonList(currency), Sort.Direction.ASC);
        VaultCustomQueryCriteria<SampleCashSchemaV2.PersistentCashState> criteria = new VaultCustomQueryCriteria(expression, Vault.StateStatus.UNCONSUMED, null);

        database.transaction(tx -> vaultService.queryBy(FungibleAsset.class, criteria));
    }

    @Test
    public void criteriaWithFieldFromMappedSuperclassOfSuperclass() throws NoSuchFieldException {
        FieldInfo quantity = getField("quantity", SampleCashSchemaV2.PersistentCashState.class);
        FieldInfo currency = getField("currency", SampleCashSchemaV2.PersistentCashState.class);
        FieldInfo stateRef = getField("stateRef", SampleCashSchemaV2.PersistentCashState.class);

        CriteriaExpression.AggregateFunctionExpression<Object, Boolean> expression = sum(quantity, asList(currency, stateRef), Sort.Direction.ASC);
        VaultCustomQueryCriteria<SampleCashSchemaV2.PersistentCashState> criteria = new VaultCustomQueryCriteria(expression, Vault.StateStatus.UNCONSUMED, null);

        database.transaction(tx -> vaultService.queryBy(FungibleAsset.class, criteria));
    }

    @Test
    public void unconsumedLinearStates() throws VaultQueryException {
        database.transaction(tx -> {
            vaultFiller.fillWithSomeTestLinearStates(3);
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
                    vaultFiller.fillWithSomeTestLinearStates(8);
                    return vaultFiller.fillWithSomeTestLinearStates(2);
                });
        database.transaction(tx -> {
            Stream<StateRef> stateRefsStream = StreamSupport.stream(issuedStates.getStates().spliterator(), false).map(StateAndRef::getRef);
            List<StateRef> stateRefs = stateRefsStream.collect(Collectors.toList());

            SortAttribute.Standard sortAttribute = new SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF_TXN_ID);
            Sort sorting = new Sort(singletonList(new Sort.SortColumn(sortAttribute, Sort.Direction.ASC)));
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
            vaultFiller.fillWithSomeTestCash(
                    new Amount<>(100, Currency.getInstance("USD")),
                    issuerServices,
                    3,
                    DUMMY_CASH_ISSUER,
                    null,
                    new Random());
            return tx;
        });
        database.transaction(tx -> {
            vaultFiller.consumeCash(amount, CHARLIE);
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
        List<String> dealIds = asList("123", "456", "789");
        @SuppressWarnings("unchecked")
        Triple<StateAndRef<LinearState>, UniqueIdentifier, Vault<DealState>> ids =
                database.transaction((DatabaseTransaction tx) -> {
                    Vault<LinearState> states = vaultFiller.fillWithSomeTestLinearStates(10, null);
                    StateAndRef<LinearState> linearState = states.getStates().iterator().next();
                    UniqueIdentifier uid = linearState.component1().getData().getLinearId();
                    Vault<DealState> dealStates = vaultFiller.fillWithSomeTestDeals(dealIds);
                    return new Triple(linearState, uid, dealStates);
                });
        database.transaction(tx -> {
            // consume states
            vaultFiller.consumeDeals((List<? extends StateAndRef<? extends DealState>>) ids.getThird().getStates());
            vaultFiller.consumeLinearStates(singletonList(ids.getFirst()));
            return tx;
        });
        database.transaction(tx -> {
            // DOCSTART VaultJavaQueryExample2
            Vault.StateStatus status = Vault.StateStatus.CONSUMED;
            @SuppressWarnings("unchecked")
            Set<Class<LinearState>> contractStateTypes = new HashSet(singletonList(LinearState.class));

            QueryCriteria vaultCriteria = new VaultQueryCriteria(status, contractStateTypes);

            List<UniqueIdentifier> linearIds = singletonList(ids.getSecond());
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
            vaultFiller.fillWithSomeTestCash(pounds, issuerServices, 1, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(dollars100, issuerServices, 1, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(dollars10, issuerServices, 1, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(dollars1, issuerServices, 1, DUMMY_CASH_ISSUER);
            return tx;
        });
        database.transaction(tx -> {
            try {
                // DOCSTART VaultJavaQueryExample3
                QueryCriteria generalCriteria = new VaultQueryCriteria(Vault.StateStatus.ALL);

                FieldInfo attributeCurrency = getField("currency", CashSchemaV1.PersistentCashState.class);
                FieldInfo attributeQuantity = getField("pennies", CashSchemaV1.PersistentCashState.class);

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
            vaultFiller.fillWithSomeTestCash(
                    new Amount<>(100, Currency.getInstance("USD")),
                    issuerServices,
                    3,
                    DUMMY_CASH_ISSUER,
                    null,
                    new Random());
            return tx;
        });
        database.transaction(tx -> {
            // DOCSTART VaultJavaQueryExample4
            @SuppressWarnings("unchecked")
            Set<Class<ContractState>> contractStateTypes = new HashSet(singletonList(Cash.State.class));

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
        List<String> dealIds = asList("123", "456", "789");
        UniqueIdentifier uid =
                database.transaction(tx -> {
                    Vault<LinearState> states = vaultFiller.fillWithSomeTestLinearStates(10, null);
                    UniqueIdentifier _uid = states.getStates().iterator().next().component1().getData().getLinearId();
                    vaultFiller.fillWithSomeTestDeals(dealIds);
                    return _uid;
                });
        database.transaction(tx -> {
            // DOCSTART VaultJavaQueryExample5
            @SuppressWarnings("unchecked")
            Set<Class<ContractState>> contractStateTypes = new HashSet(asList(DealState.class, LinearState.class));
            QueryCriteria vaultCriteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, contractStateTypes);

            List<UniqueIdentifier> linearIds = singletonList(uid);
            List<AbstractParty> dealParty = singletonList(MEGA_CORP.getParty());
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
            vaultFiller.fillWithSomeTestCash(dollars100, issuerServices, 1, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(dollars200, issuerServices, 2, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(dollars300, issuerServices, 3, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(pounds, issuerServices, 4, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(swissfrancs, issuerServices, 5, DUMMY_CASH_ISSUER);
            return tx;
        });
        database.transaction(tx -> {
            try {
                // DOCSTART VaultJavaQueryExample21
                FieldInfo pennies = getField("pennies", CashSchemaV1.PersistentCashState.class);

                QueryCriteria sumCriteria = new VaultCustomQueryCriteria(sum(pennies));
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
            vaultFiller.fillWithSomeTestCash(dollars100, issuerServices, 1, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(dollars200, issuerServices, 2, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(dollars300, issuerServices, 3, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(pounds, issuerServices, 4, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(swissfrancs, issuerServices, 5, DUMMY_CASH_ISSUER);
            return tx;
        });
        database.transaction(tx -> {
            try {
                // DOCSTART VaultJavaQueryExample22
                FieldInfo pennies = getField("pennies", CashSchemaV1.PersistentCashState.class);
                FieldInfo currency = getField("currency", CashSchemaV1.PersistentCashState.class);

                QueryCriteria sumCriteria = new VaultCustomQueryCriteria(sum(pennies, singletonList(currency)));
                QueryCriteria countCriteria = new VaultCustomQueryCriteria(Builder.count(pennies));
                QueryCriteria maxCriteria = new VaultCustomQueryCriteria(Builder.max(pennies, singletonList(currency)));
                QueryCriteria minCriteria = new VaultCustomQueryCriteria(Builder.min(pennies, singletonList(currency)));
                QueryCriteria avgCriteria = new VaultCustomQueryCriteria(Builder.avg(pennies, singletonList(currency)));

                QueryCriteria criteria = sumCriteria.and(countCriteria).and(maxCriteria).and(minCriteria).and(avgCriteria);
                Vault.Page<Cash.State> results = vaultService.queryBy(Cash.State.class, criteria);
                // DOCEND VaultJavaQueryExample22

                assertThat(results.getOtherResults()).hasSize(18);
                /* CHF */
                assertThat(results.getOtherResults().get(0)).isEqualTo(500L);
                assertThat(results.getOtherResults().get(1)).isEqualTo(5L);
                assertThat(results.getOtherResults().get(2)).isEqualTo(102L);
                assertThat(results.getOtherResults().get(3)).isEqualTo(94L);
                assertThat(results.getOtherResults().get(4)).isEqualTo(100.00);
                assertThat(results.getOtherResults().get(5)).isEqualTo("CHF");
                /* GBP */
                assertThat(results.getOtherResults().get(6)).isEqualTo(400L);
                assertThat(results.getOtherResults().get(7)).isEqualTo(4L);
                assertThat(results.getOtherResults().get(8)).isEqualTo(103L);
                assertThat(results.getOtherResults().get(9)).isEqualTo(93L);
                assertThat(results.getOtherResults().get(10)).isEqualTo(100.0);
                assertThat(results.getOtherResults().get(11)).isEqualTo("GBP");
                /* USD */
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
            vaultFiller.fillWithSomeTestCash(dollars100, issuerServices, 1, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(dollars200, issuerServices, 2, BOC.ref((byte) '1'));
            vaultFiller.fillWithSomeTestCash(pounds300, issuerServices, 3, DUMMY_CASH_ISSUER);
            vaultFiller.fillWithSomeTestCash(pounds400, issuerServices, 4, BOC.ref((byte) '1'));
            return tx;
        });
        database.transaction(tx -> {
            try {
                // DOCSTART VaultJavaQueryExample23
                FieldInfo pennies = getField("pennies", CashSchemaV1.PersistentCashState.class);
                FieldInfo currency = getField("currency", CashSchemaV1.PersistentCashState.class);
                FieldInfo issuerPartyHash = getField("issuerPartyHash", CashSchemaV1.PersistentCashState.class);
                QueryCriteria sumCriteria = new VaultCustomQueryCriteria(sum(pennies, asList(issuerPartyHash, currency), Sort.Direction.DESC));
                Vault.Page<Cash.State> results = vaultService.queryBy(Cash.State.class, sumCriteria);
                // DOCEND VaultJavaQueryExample23

                assertThat(results.getOtherResults()).hasSize(12);

                assertThat(results.getOtherResults().get(0)).isEqualTo(400L);
                assertThat(results.getOtherResults().get(1)).isEqualTo(CryptoUtils.toStringShort(BOC.getPublicKey()));
                assertThat(results.getOtherResults().get(2)).isEqualTo("GBP");
                assertThat(results.getOtherResults().get(3)).isEqualTo(300L);
                assertThat(results.getOtherResults().get(4)).isEqualTo(CryptoUtils.toStringShort(DUMMY_CASH_ISSUER_INFO.getPublicKey()));
                assertThat(results.getOtherResults().get(5)).isEqualTo("GBP");
                assertThat(results.getOtherResults().get(6)).isEqualTo(200L);
                assertThat(results.getOtherResults().get(7)).isEqualTo(CryptoUtils.toStringShort(BOC.getPublicKey()));
                assertThat(results.getOtherResults().get(8)).isEqualTo("USD");
                assertThat(results.getOtherResults().get(9)).isEqualTo(100L);
                assertThat(results.getOtherResults().get(10)).isEqualTo(CryptoUtils.toStringShort(DUMMY_CASH_ISSUER_INFO.getPublicKey()));
                assertThat(results.getOtherResults().get(11)).isEqualTo("USD");

            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
            return tx;
        });
    }

    private List<StateAndRef<Cash.State>> queryStatesWithPaging(VaultService vaultService, int pageSize) {
        // DOCSTART VaultQueryExample24
        int pageNumber = DEFAULT_PAGE_NUM;
        List<StateAndRef<Cash.State>> states = new ArrayList<>();
        long totalResults;
        do {
            PageSpecification pageSpec = new PageSpecification(pageNumber, pageSize);
            Vault.Page<Cash.State> results = vaultService.queryBy(Cash.State.class, new VaultQueryCriteria(), pageSpec);
            totalResults = results.getTotalStatesAvailable();
            List<StateAndRef<Cash.State>> newStates = results.getStates();
            System.out.println(newStates.size());
            states.addAll(results.getStates());
            pageNumber++;
        } while ((pageSize * (pageNumber - 1) <= totalResults));
        // DOCEND VaultQueryExample24
        return states;
    }

    @Test
    public void testExampleOfQueryingStatesWithPagingWorksCorrectly() {
        Amount<Currency> dollars100 = new Amount<>(100, Currency.getInstance("USD"));
        database.transaction(tx -> {
            vaultFiller.fillWithSomeTestCash(dollars100, issuerServices, 4, DUMMY_CASH_ISSUER);
            return tx;
        });
        database.transaction(tx -> {
            assertThat(queryStatesWithPaging(vaultService, 5).size()).isEqualTo(4);
            vaultFiller.fillWithSomeTestCash(dollars100, issuerServices, 1, DUMMY_CASH_ISSUER);
            return tx;
        });
        database.transaction(tx -> {
            assertThat(queryStatesWithPaging(vaultService, 5).size()).isEqualTo(5);
            vaultFiller.fillWithSomeTestCash(dollars100, issuerServices, 1, DUMMY_CASH_ISSUER);
            return tx;
        });

        database.transaction(tx -> {
            assertThat(queryStatesWithPaging(vaultService, 5).size()).isEqualTo(6);
            return tx;
        });
    }
}
