#include <iostream>
#include "List.h"

#include "debug.h"
#include "colours.h"

#include "schema/Composite.h"

/******************************************************************************/

namespace {

    std::pair<std::string, std::string>
    listType (const std::string & list_) {
        auto pos = list_.find ('<');

        return std::make_pair (
               std::string { list_.substr (0, pos) },
                std::string { list_.substr(pos + 1, list_.size() - pos - 2) }
        );
    }
}

/******************************************************************************/

amqp::internal::schema::
List::List (
    uPtr<Descriptor> & descriptor_,
    const std::string & name_,
    const std::string & label_,
    const std::vector<std::string> & provides_,
    const std::string & source_
) : Restricted (
        descriptor_,
        name_,
        label_,
        provides_,
        amqp::internal::schema::Restricted::RestrictedTypes::List)
  , m_listOf { listType(name_).second }
{

}

/******************************************************************************/

std::vector<std::string>::const_iterator
amqp::internal::schema::
List::begin() const {
    return m_listOf.begin();
}

/******************************************************************************/

std::vector<std::string>::const_iterator
amqp::internal::schema::
List::end() const {
    return m_listOf.end();
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
List::listOf() const {
    return m_listOf[0];
}

/******************************************************************************/

int
amqp::internal::schema::
List::dependsOn (const amqp::internal::schema::Restricted & lhs_) const {
    auto rtn { 0 };
    switch (lhs_.restrictedType()) {
        case RestrictedTypes::List : {
            const auto & list { dynamic_cast<const List &>(lhs_) };

            // does the left hand side depend on us
            DBG ("  L/L a) " << list.listOf() << " == " << name() << std::endl); // NOLINT
            if (list.listOf() == name()) {
                rtn = 1;
            }

            // do we depend on the lhs
            DBG ("  L/L b) " << listOf() << " == " << list.name() << std::endl); // NOLINT
            if (listOf() == list.name()) {
                rtn = 2;
            }
        }
    }

    return rtn;
}

/******************************************************************************/

int
amqp::internal::schema::
List::dependsOn (const amqp::internal::schema::Composite & lhs_) const {
    auto rtn { 0 };
    for (const auto & field : lhs_.fields()) {
        DBG ("  L/C a) " << field->resolvedType() << " == " << name() << std::endl); // NOLINT
        if (field->resolvedType() == name()) {
            rtn = 1;
        }
    }

    DBG ("  L/C b) " << listOf() << " == " << lhs_.name() << std::endl); // NOLINT
    if (listOf() == lhs_.name()) {
        rtn = 2;
    }

    return rtn;
}

/*********************************************************o*********************/
