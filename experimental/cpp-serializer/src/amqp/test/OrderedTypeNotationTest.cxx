#include <gtest/gtest.h>

#include "OrderedTypeNotations.h"

/******************************************************************************/

namespace {

    class OTN : public amqp::internal::schema::OrderedTypeNotation {
        private :
            std::string m_name;
            std::vector<std::string> m_dependsOn;
        public :
            OTN(std::string name_, std::vector<std::string> dependsOn_)
                : m_name (std::move (name_))
                , m_dependsOn (std::move (dependsOn_))
            { }


            int dependsOn (const OrderedTypeNotation & otn_) const override {
                const auto & otn = dynamic_cast<const OTN &>(otn_);

                // does the "left hand side" depend on us (in this case
                // the lhs is us as we're not inverting
                if (std::find (
                        m_dependsOn.begin(),
                        m_dependsOn.end(),
                        otn.name()) != m_dependsOn.end())
                {
                    return 1;
                }

                // do we depend on the left hand side

                if (std::find (
                        otn.begin(),
                        otn.end(),
                        name()) != otn.end())
                {
                    return 2;
                }

                return 0;
            }

            const std::string & name() const { return m_name; }

            decltype(m_dependsOn.cbegin()) begin() const { return m_dependsOn.cbegin(); }
            decltype(m_dependsOn.cend()) end() const { return m_dependsOn.cend(); }
    };

    inline
    std::string
    str (const amqp::internal::schema::OrderedTypeNotations<OTN> & list_) {
        std::stringstream ss;
        ss << list_;
        return ss.str();
    }

}

/******************************************************************************/

/**
 * Makes testing easier if we compress the list into a flat series rather than
 * being all fancy with our output
 */
template<>
std::ostream &
operator << (
        std::ostream &stream_,
        const amqp::internal::schema::OrderedTypeNotations<OTN> &otn_
) {
    auto first { true };
    for (const auto & i : otn_.m_schemas) {
        for (const auto & j : i) {
            if (first) {
                first = false;
            } else {
                stream_ << " ";
            }
            stream_ << j->name();
        }
    }

    return stream_;
}

/******************************************************************************/

TEST (OTNTest, singleInsert) { // NOLINT
    amqp::internal::schema::OrderedTypeNotations<OTN> list;

    list.insert(std::make_unique<OTN>("A", std::vector<std::string>()));
    ASSERT_EQ("A", str (list));
}

/******************************************************************************/

TEST (OTNTest, twoInserts) { // NOLINT
    std::cout << std::endl;
    amqp::internal::schema::OrderedTypeNotations<OTN> list;

    list.insert(std::make_unique<OTN>("A", std::vector<std::string>()));
    list.insert(std::make_unique<OTN>("B", std::vector<std::string>()));
    ASSERT_EQ("A B", str (list));
}

/******************************************************************************/

TEST (OTNTest, A_depends_on_B) { // NOLINT
    std::cout << std::endl;
    amqp::internal::schema::OrderedTypeNotations<OTN> list;

    std::vector<std::string> aDeps = { "B" };
    list.insert(std::make_unique<OTN>("A", aDeps));
    list.insert(std::make_unique<OTN>("B", std::vector<std::string>()));
    ASSERT_EQ("A B", str (list));
}

/******************************************************************************/

TEST (OTNTest, B_depends_on_A) { // NOLINT
    std::cout << std::endl;
    amqp::internal::schema::OrderedTypeNotations<OTN> list;

    std::vector<std::string> aDeps = { };
    std::vector<std::string> bDeps = { "A" };

    list.insert(std::make_unique<OTN>("A", aDeps));
    list.insert(std::make_unique<OTN>("B", bDeps));

    ASSERT_EQ ("B A", str (list));
}

/******************************************************************************/

TEST (OTNTest, three_1) { // NOLINT
    std::cout << std::endl;
    amqp::internal::schema::OrderedTypeNotations<OTN> list;

    std::vector<std::string> aDeps = { };
    std::vector<std::string> bDeps = { "A" };
    std::vector<std::string> cDeps = { "A" };

    list.insert(std::make_unique<OTN>("A", aDeps));
    list.insert(std::make_unique<OTN>("B", bDeps));
    list.insert(std::make_unique<OTN>("C", cDeps));

    ASSERT_EQ ("B C A", str (list));
}

/******************************************************************************/

TEST (OTNTest, three_2) { // NOLINT
    std::cout << std::endl;
    amqp::internal::schema::OrderedTypeNotations<OTN> list;

    std::vector<std::string> aDeps = { "B" };
    std::vector<std::string> bDeps = { "C" };
    std::vector<std::string> cDeps = {  };

    list.insert(std::make_unique<OTN>("A", aDeps));
    list.insert(std::make_unique<OTN>("B", bDeps));
    list.insert(std::make_unique<OTN>("C", cDeps));

    EXPECT_EQ ("A B C", str (list));
}

/******************************************************************************/

TEST (OTNTest, three_3) { // NOLINT
    std::cout << std::endl;
    amqp::internal::schema::OrderedTypeNotations<OTN> list;

    std::vector<std::string> aDeps = { "B" };
    std::vector<std::string> bDeps = { "C" };
    std::vector<std::string> cDeps = {  };

    list.insert(std::make_unique<OTN>("C", cDeps));
    list.insert(std::make_unique<OTN>("A", aDeps));
    list.insert(std::make_unique<OTN>("B", bDeps));

    EXPECT_EQ ("A B C", str (list));
}

/******************************************************************************/

TEST (OTNTest, three_4) { // NOLINT
    std::cout << std::endl;
    amqp::internal::schema::OrderedTypeNotations<OTN> list;

    std::vector<std::string> aDeps = { "B" };
    std::vector<std::string> bDeps = { "C" };
    std::vector<std::string> cDeps = {  };

    list.insert(std::make_unique<OTN>("C", cDeps));
    list.insert(std::make_unique<OTN>("B", bDeps));
    list.insert(std::make_unique<OTN>("A", aDeps));

    EXPECT_EQ ("A B C", str (list));
}

/******************************************************************************/

TEST (OTNTest, three_5) { // NOLINT
    std::cout << std::endl;
    amqp::internal::schema::OrderedTypeNotations<OTN> list;

    std::vector<std::string> aDeps = { "B" };
    std::vector<std::string> bDeps = { "C" };
    std::vector<std::string> cDeps = {  };

    list.insert(std::make_unique<OTN>("B", bDeps));
    list.insert(std::make_unique<OTN>("C", cDeps));
    list.insert(std::make_unique<OTN>("A", aDeps));

    EXPECT_EQ ("A B C", str (list));
}

/******************************************************************************/
