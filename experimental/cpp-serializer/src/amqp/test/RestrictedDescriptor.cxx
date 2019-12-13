#include <gtest/gtest.h>

#include "descriptors/corda-descriptors/RestrictedDescriptor.h"

using namespace amqp::internal::schema::descriptors;

TEST (RestrictedDescriptor, makePrim1) { // NOLINT
    EXPECT_EQ ("int", RestrictedDescriptor::makePrim ("int"));
}

TEST (RestrictedDescriptor, makePrim2) { // NOLINT
    EXPECT_EQ ("int[]", RestrictedDescriptor::makePrim ("int[]"));
}

TEST (RestrictedDescriptor, makePrim3) { // NOLINT
    EXPECT_EQ ("java.lang.integer", RestrictedDescriptor::makePrim ("int"));
}

TEST (RestrictedDescriptor, makePrim4) { // NOLINT
    EXPECT_EQ ("java.lang.integer[]", RestrictedDescriptor::makePrim ("int[]"));
}

TEST (RestrictedDescriptor, makePrim5) { // NOLINT§
    EXPECT_EQ ("int[], int", RestrictedDescriptor::makePrim ("java.lang.Integer[], java.lang.Integer"));
}
