package net.corda.core.schemas;

import javax.persistence.*;
import java.util.Arrays;

public class BadSchemaNoGetterJavaV1 extends MappedSchema {

    public BadSchemaNoGetterJavaV1() {
        super(TestJavaSchemaFamily.class, 1, Arrays.asList(State.class));
    }

    @Entity
    public static class State extends PersistentState {
        @JoinColumns({@JoinColumn(name = "itid"), @JoinColumn(name = "outid")})
        @OneToOne
        @MapsId
        public GoodSchemaJavaV1.State other;
        private String id;

        @Column
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
