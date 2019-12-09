#include <gtest/gtest.h>

#include "TestUtils.h"

#include "amqp/schema/described-types/Descriptor.h"
#include "restricted-types/Map.h"
#include "restricted-types/List.h"
#include "restricted-types/Enum.h"

/******************************************************************************
 *
 * mapType Tests
 *
 ******************************************************************************/

TEST (Map, name1) {
    auto [map, of, to] = amqp::internal::schema::Map::mapType (
            "java.util.Map<int, string>");

    ASSERT_EQ ("java.util.Map", map);
    ASSERT_EQ ("int", of);
    ASSERT_EQ ("string", to);
}

/******************************************************************************/

TEST (Map, name2) {
    auto [map, of, to] = amqp::internal::schema::Map::mapType (
            "java.util.Map<int, java.util.List<string>>");

    ASSERT_EQ ("java.util.Map", map);
    ASSERT_EQ ("int", of);
    ASSERT_EQ ("java.util.List<string>", to);
}

/******************************************************************************/

TEST (Map, name3) {
    auto [map, of, to] = amqp::internal::schema::Map::mapType (
            "java.util.Map<java.util.Pair<int, int>, java.util.List<string>>");

    ASSERT_EQ ("java.util.Map", map);
    ASSERT_EQ ("java.util.Pair<int, int>", of);
    ASSERT_EQ ("java.util.List<string>", to);
}

/******************************************************************************
 *
 *
 *
 ******************************************************************************/

using namespace amqp::internal::schema;

/******************************************************************************/

TEST (Map, dependsOn1) {
    auto result {
            "level 1\n"
            "    * java.util.List<string>\n"
            "\n"
            "level 2\n"
            "    * java.util.Map<int, java.util.List<string>>\n\n"
    };

    {
        OrderedTypeNotations<Restricted> otn;

        auto l = test::list("string");
        auto m = test::map("int", l->name());

        otn.insert (std::move (l));
        otn.insert (std::move (m));

        std::stringstream ss;
        ss << otn;

        ASSERT_EQ (result, ss.str());
    }

    // Same test but reverse the insertion order
    {
        OrderedTypeNotations<Restricted> otn;

        auto l = test::list("string");
        auto m = test::map("int", l->name());

        otn.insert(std::move(m));
        otn.insert(std::move(l));

        std::stringstream ss;
        ss << otn;

        ASSERT_EQ (result, ss.str());
    }
}

/******************************************************************************/

TEST (Map, dependsOn2) {
    auto result {
        "level 1\n"
        "    * net.corda.eee\n"
        "\n"
        "level 2\n"
        "    * java.util.List<net.corda.eee>\n"
        "\n"
        "level 3\n"
        "    * java.util.Map<int, java.util.List<net.corda.eee>>\n\n"
    };

    {
        OrderedTypeNotations<Restricted> otn;

        auto e = test::eNum("eee");
        auto l = test::list(e->name());
        auto m = test::map("int", l->name());

        otn.insert(std::move(l));
        otn.insert(std::move(m));
        otn.insert(std::move(e));

        std::stringstream ss;
        ss << otn;

        ASSERT_EQ (result, ss.str());
    }
    {
        OrderedTypeNotations<Restricted> otn;

        auto e = test::eNum("eee");
        auto l = test::list(e->name());
        auto m = test::map("int", l->name());

        otn.insert(std::move(l));
        otn.insert(std::move(e));
        otn.insert(std::move(m));

        std::stringstream ss;
        ss << otn;

        ASSERT_EQ (result, ss.str());
    }
    {
        OrderedTypeNotations<Restricted> otn;

        auto e = test::eNum("eee");
        auto l = test::list(e->name());
        auto m = test::map("int", l->name());

        otn.insert(std::move(m));
        otn.insert(std::move(l));
        otn.insert(std::move(e));

        std::stringstream ss;
        ss << otn;

        ASSERT_EQ (result, ss.str());
    }
    {
        OrderedTypeNotations<Restricted> otn;

        auto e = test::eNum ("eee");
        auto l = test::list (e->name());
        auto m = test::map ("int", l->name());

        otn.insert (std::move (m));

        otn.insert (std::move (e));
        otn.insert (std::move (l));

        std::stringstream ss;
        ss << otn;

        ASSERT_EQ (result, ss.str());
    }
    {
        OrderedTypeNotations<Restricted> otn;

        auto e = test::eNum("eee");
        auto l = test::list(e->name());
        auto m = test::map("int", l->name());

        otn.insert (std::move (e));
        otn.insert (std::move (l));
        otn.insert (std::move (m));

        std::stringstream ss;
        ss << otn;

        ASSERT_EQ (result, ss.str());
    }
    {
        OrderedTypeNotations<Restricted> otn;

        auto e = test::eNum ("eee");
        auto l = test::list (e->name());
        auto m = test::map ("int", l->name());

        otn.insert (std::move (e));
        otn.insert (std::move (m));
        otn.insert (std::move (l));

        std::stringstream ss;
        ss << otn;

        ASSERT_EQ (result, ss.str());
    }
}

/******************************************************************************/

