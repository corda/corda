package net.corda.core.schemas;

import javax.persistence.*;
import java.util.Arrays;

public class BadSchemaJavaV1 extends MappedSchema {

    public BadSchemaJavaV1() {
        super(TestJavaSchemaFamily.class, 1, Arrays.asList(State.class));
    }

    @Entity
    public static class State extends PersistentState {
        private String id;
        private GoodSchemaJavaV1.State other;

        @Column
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @JoinColumns({@JoinColumn(name = "itid"), @JoinColumn(name = "outid")})
        @OneToOne
        @MapsId
        public GoodSchemaJavaV1.State getOther() {
            return other;
        }

        public void setOther(GoodSchemaJavaV1.State other) {
            this.other = other;
        }
    }
}