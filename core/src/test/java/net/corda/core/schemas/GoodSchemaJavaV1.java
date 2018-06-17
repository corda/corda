package net.corda.core.schemas;

import javax.persistence.Column;
import javax.persistence.Entity;
import java.util.Arrays;

public class GoodSchemaJavaV1 extends MappedSchema {

    public GoodSchemaJavaV1() {
        super(TestJavaSchemaFamily.class, 1, Arrays.asList(State.class));
    }

    @Entity
    public static class State extends PersistentState {
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