package net.corda.core.schemas;

import javax.persistence.Column;
import javax.persistence.Entity;
import java.util.Arrays;

public class TrickySchemaJavaV1 extends MappedSchema {

    public TrickySchemaJavaV1() {
        super(TestJavaSchemaFamily.class, 1, Arrays.asList(TrickySchemaJavaV1.State.class));
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

        //the field is from other schema bu it's not persisted one (no JPA annotation)
        public GoodSchemaJavaV1.State getOther() {
            return other;
        }

        public void setOther(GoodSchemaJavaV1.State other) {
            this.other = other;
        }
    }
}