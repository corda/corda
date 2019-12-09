#include <iostream>
#include "List.h"
#include "Map.h"
#include "Enum.h"

#include "debug.h"
#include "colours.h"

#include "amqp/schema/described-types/Composite.h"

/******************************************************************************
 *
 * Static member functions
 *
 ******************************************************************************/

std::pair<std::string, std::string>
amqp::internal::schema::
List::listType (const std::string & list_) {
    auto pos = list_.find ('<');

    return std::make_pair (
           std::string { unbox (list_.substr (0, pos)) },
           std::string { unbox (list_.substr(pos + 1, list_.size() - pos - 2)) }
    );
}

/******************************************************************************
 *
 * Non static member functions
 *
 ******************************************************************************/

amqp::internal::schema::
List::List (
    uPtr<Descriptor> descriptor_,
    std::string name_,
    std::string label_,
    std::vector<std::string> provides_,
    std::string source_
) : Restricted (
        std::move (descriptor_),
        std::move (name_),
        std::move (label_),
        std::move (provides_),
        amqp::internal::schema::Restricted::RestrictedTypes::list_t)
  , m_listOf { listType (name()).second }
  , m_source { std::move (source_) }
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
List::dependsOnMap (const amqp::internal::schema::Map & map_) const {
    // do we depend on the lhs
    if (listOf() == map_.name()) {
        return 1;
    }

    // does lhs_ depend on us
    auto lhsMapOf { map_.mapOf() };

    if (lhsMapOf.first.get() == name() || lhsMapOf.second.get() == name()) {
        return 2;
    }

    return 0;
}

/******************************************************************************/

int
amqp::internal::schema::
List::dependsOnList (const amqp::internal::schema::List & list_) const {
    // do we depend on the lhs
    if (listOf() == list_.name()) {
        return 1;
    }

    // does the left hand side depend on us
    if (list_.listOf() == name()) {
        return 2;
    }

    return 0;
}

/******************************************************************************/

int
amqp::internal::schema::
List::dependsOnArray (const amqp::internal::schema::Array & array_) const {
    return 0;
}

/******************************************************************************/

int
amqp::internal::schema::
List::dependsOnEnum (const amqp::internal::schema::Enum & enum_) const {
    // an enum cannot depend on us so don't bother checking, lets just check
    // if we depend on it
    if (listOf() == enum_.name()) {
        return 1;
    }

    return 0;
}

/******************************************************************************/

int
amqp::internal::schema::
List::dependsOnRHS (const amqp::internal::schema::Composite & lhs_) const {
    if (listOf() == lhs_.name()) {
        return 1;
    }

    for (const auto & field : lhs_.fields()) {
        if (field->resolvedType() == name()) {
            return 2;
        }
    }

    return 0;
}

/*********************************************************o*********************/
