#include <gtest/gtest.h>
#include <memory>
#include <string>

#include "Reader.h"

/******************************************************************************/

using namespace amqp::reader;
using namespace amqp::internal::reader;

/******************************************************************************/

TEST (Pair, string) { // NOLINT
    TypedPair<std::string> str_test ("Left", "Hello");

    EXPECT_EQ("Left : Hello", str_test.dump());
}

/******************************************************************************/

TEST (Pair, int) { // NOLINT
    TypedPair<int> int_test ("Left", 101);

    EXPECT_EQ("Left : 101", int_test.dump());
}

/******************************************************************************/

TEST (Pair, UP1) { // NOLINT
    std::unique_ptr<TypedPair<double>> test =
        std::make_unique<TypedPair<double>> ("property", 10.0);

    EXPECT_EQ("property : 10.000000", test->dump());
}

/******************************************************************************/

TEST (Pair, UP2) { // NOLINT
    struct builder {
        static std::unique_ptr<IValue>
        build (const std::string & prop_, int val_) {
            return std::make_unique<TypedPair<int>> (prop_, val_);
        }
    };

    std::vector<std::unique_ptr<IValue>> vec;
    vec.reserve(2);

    vec.emplace_back (builder::build ("first",  1));
    vec.emplace_back (builder::build ("second", 2));

    std::unique_ptr<Pair> test =
        std::make_unique<TypedPair<std::vector<std::unique_ptr<IValue>>>> (
            "Vector", std::move (vec));

    EXPECT_EQ("Vector : { first : 1, second : 2 }", test->dump());
}

/******************************************************************************/

