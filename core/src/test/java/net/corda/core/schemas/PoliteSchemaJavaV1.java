/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.schemas;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import java.util.Arrays;

public class PoliteSchemaJavaV1 extends MappedSchema {

    public PoliteSchemaJavaV1() {
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

        @Transient
        public GoodSchemaJavaV1.State getOther() {
            return other;
        }

        public void setOther(GoodSchemaJavaV1.State other) {
            this.other = other;
        }
    }
}