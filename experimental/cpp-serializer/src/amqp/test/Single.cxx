#include <gtest/gtest.h>
#include <memory>
#include <string>

#include "Reader.h"

TEST (Single, string) { // NOLINT
    amqp::TypedSingle<std::string> str_test ("Hello");

    EXPECT_EQ("Hello", str_test.dump());
}

TEST (Single, list) { // NOLINT

    struct builder {
        static std::unique_ptr<amqp::Value>
        build (int val_) {
            return std::make_unique<amqp::TypedSingle<int>> (val_);
        }
    };

    std::list<std::unique_ptr<amqp::Value>> list;

    list.emplace_back (builder::build (1));
    list.emplace_back (builder::build (2));
    list.emplace_back (builder::build (3));
    list.emplace_back (builder::build (4));
    list.emplace_back (builder::build (5));

    std::unique_ptr<amqp::Single> test =
            std::make_unique<amqp::TypedSingle<std::list<std::unique_ptr<amqp::Value>>>> (
                    std::move (list));

    EXPECT_EQ("[ 1, 2, 3, 4, 5 ]", test->dump());
}
