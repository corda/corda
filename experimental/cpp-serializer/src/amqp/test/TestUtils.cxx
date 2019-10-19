#include "TestUtils.h"

#include <algorithm>
#include <string>
#include "types.h"

#include "restricted-types/Map.h"
#include "restricted-types/List.h"
#include "restricted-types/Enum.h"

/******************************************************************************/

using namespace amqp::internal::schema;

/******************************************************************************/

namespace {

    std::string fingerprint() {
        auto randchar = []() -> char {
            const char charset[] =
                    "0123456789"
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    "abcdefghijklmnopqrstuvwxyz";
            const size_t max_index = (sizeof(charset) - 1);
            return charset[rand() % max_index];
        };
        std::string str(20, 0);
        std::generate_n(str.begin(), 20, randchar);

        return "net.corda:" + str;
    }

}

/******************************************************************************/

uPtr<Map>
test::
map (const std::string & of_, const std::string & to_) {
    auto desc = std::make_unique<Descriptor> (
            fingerprint());

    std::vector<std::string> provides { };

    return std::make_unique<Map>(
            std::move (desc),
            "java.util.Map<" + of_ + ", " + to_ + ">",
            "label",
            std::move (provides),
            "map"
    );
}

/******************************************************************************/

uPtr <amqp::internal::schema::List>
test::
list (const std::string & of_) {
    auto desc = std::make_unique<Descriptor> (
            fingerprint());

    std::vector<std::string> provides { };

    std::cout << "FAKE LIST: " << of_ << std::endl;

    return std::make_unique<List> (
            std::move (desc),
            "java.util.List<" + of_ + ">",
            "label",
            std::move (provides),
            "map"
    );
}

/******************************************************************************/

uPtr <amqp::internal::schema::Enum>
test::
eNum (const std::string & e_) {
    auto desc = std::make_unique<amqp::internal::schema::Descriptor> (
            fingerprint());

    sVec<std::string> provides { };

    sVec<uPtr<Choice>> choices (2);
    choices.emplace_back(
            (std::make_unique<Choice>(Choice ("a"))));
    choices.emplace_back(
            (std::make_unique<Choice>(Choice ("b"))));

    return std::make_unique<amqp::internal::schema::Enum>(
            std::move (desc),
            "net.corda." + e_,
            "label",
            std::move (provides),
            "enum",
            std::move (choices)
    );

}

/******************************************************************************/

uPtr<amqp::internal::schema::Composite>
test::
comp (
        const std::string & name_,
        const std::vector<std::string> & fields_
) {
    /*
    auto desc = std::make_unique<Descriptor> (
            fingerprint());

    std::vector<std::string> provides { };

    Composite (
    name_,
    "label",
    provides,
    desc,
    std::vector<std::unique_ptr<Field>> & fields_);
    */
}

/******************************************************************************/
