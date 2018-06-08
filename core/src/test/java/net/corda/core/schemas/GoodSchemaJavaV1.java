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