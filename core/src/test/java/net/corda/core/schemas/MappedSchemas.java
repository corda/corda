package net.corda.core.schemas;

import net.corda.finance.schemas.CashSchema;

import javax.persistence.*;
import java.util.Arrays;

//Test schemas with annotated getters rather than fields
public class MappedSchemas {

    public static class GoodSchemaJava extends MappedSchema {

        private static final GoodSchemaJava INSTANCE = new GoodSchemaJava();

        public static GoodSchemaJava getInstance() {
            return INSTANCE;
        }

        private GoodSchemaJava() {
            super(CashSchema.class, 1, Arrays.asList(State.class));
        }

        @javax.persistence.Entity
        public class State extends PersistentState {
            @Column
            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            private String id;
        }
    }

    public static class BadSchemaJava extends MappedSchema {

        private static final BadSchemaJava INSTANCE = new BadSchemaJava();

        public static BadSchemaJava getInstance() {
            return INSTANCE;
        }

        private BadSchemaJava() {
            super(CashSchema.class, 1, Arrays.asList(State.class));
        }

        @javax.persistence.Entity
        public class State extends PersistentState {
            @Column
            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            private String id;

            @JoinColumns({@JoinColumn(name = "itid"), @JoinColumn(name = "outid")})
            @OneToOne
            @MapsId
            public GoodSchemaJava.State getOther() {
                return other;
            }

            public void setOther(GoodSchemaJava.State other) {
                this.other = other;
            }

            private GoodSchemaJava.State other;
        }
    }

    public static class BadSchemaNoGetterJava extends MappedSchema {

        private static final BadSchemaNoGetterJava INSTANCE = new BadSchemaNoGetterJava();

        public static BadSchemaNoGetterJava getInstance() {
            return INSTANCE;
        }

        private BadSchemaNoGetterJava() {
            super(CashSchema.class, 1, Arrays.asList(State.class));
        }

        @javax.persistence.Entity
        public class State extends PersistentState {
            @Column
            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            private String id;

            @JoinColumns({@JoinColumn(name = "itid"), @JoinColumn(name = "outid")})
            @OneToOne
            @MapsId
            public GoodSchemaJava.State other;
        }
    }

    public static class PoliteSchemaJava extends MappedSchema {

        private static final PoliteSchemaJava INSTANCE = new PoliteSchemaJava();

        public static PoliteSchemaJava getInstance() {
            return INSTANCE;
        }

        private PoliteSchemaJava() {
            super(CashSchema.class, 1, Arrays.asList(State.class));
        }

        @javax.persistence.Entity
        public class State extends PersistentState {
            @Column
            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            private String id;

            @Transient
            public GoodSchemaJava.State getOther() {
                return other;
            }

            public void setOther(GoodSchemaJava.State other) {
                this.other = other;
            }

            private GoodSchemaJava.State other;
        }
    }

    public static class TrickySchemaJava extends MappedSchema {

        private static final TrickySchemaJava INSTANCE = new TrickySchemaJava();

        public static TrickySchemaJava getInstance() {
            return INSTANCE;
        }

        private TrickySchemaJava() {
            super(CashSchema.class, 1, Arrays.asList(State.class));
        }

        @Entity
        public class State extends PersistentState {
            @Column
            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            private String id;

            //the field is from other schema bu it's not persisted one (no JPA annotation)
            public GoodSchemaJava.State getOther() {
                return other;
            }

            public void setOther(GoodSchemaJava.State other) {
                this.other = other;
            }

            private GoodSchemaJava.State other;
        }
    }
}
