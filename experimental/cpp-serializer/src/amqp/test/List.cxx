#include <gtest/gtest.h>

#include "restricted-types/List.h"
#include "restricted-types/Restricted.h"
#include "TestUtils.h"

/******************************************************************************/

TEST (List, name) {
    auto rtn = amqp::internal::schema::List::listType("java.util.list<int>");

    ASSERT_EQ ("java.util.list", rtn.first);
    ASSERT_EQ ("int", rtn.second);
}

/******************************************************************************/

TEST (List, dependsOn1) {
    auto list1 = test::list ("string");
    auto list2 = test::list (list1->name());

    ASSERT_EQ (1, list1->dependsOn (*list2));
    ASSERT_EQ (2, list2->dependsOn (*list1));
}

/******************************************************************************/
