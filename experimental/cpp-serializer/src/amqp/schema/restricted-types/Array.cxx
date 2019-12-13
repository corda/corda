#include "Array.h"

#include "Map.h"
#include "List.h"
#include "Enum.h"
#include "amqp/schema/described-types/Composite.h"

/******************************************************************************
 *
 * Static member functions
 *
 ******************************************************************************/

namespace {

    const std::string array { "[]" };
    const std::string primArray { "[p]" };

}

std::string
amqp::internal::schema::
Array::arrayType (const std::string & array_) {
    auto pos = array_.find ('[');
    return std::string { array_.substr (0, pos) };
}

/**
 * when C++20 is done we can use .endswith, until then we have to do a reverse search
 */
bool
amqp::internal::schema::
Array::isArrayType (const std::string & type_) {
    return (std::equal (
                type_.rbegin(), type_.rbegin() + array.size(),
                array.rbegin(), array.rend())
        || std::equal (
                type_.rbegin(), type_.rbegin() + primArray.size(),
                primArray.rbegin(), primArray.rend()));
}

/******************************************************************************
 *
 * Non static member functions
 *
 ******************************************************************************/

amqp::internal::schema::
Array::Array (
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
        amqp::internal::schema::Restricted::RestrictedTypes::array_t)
  , m_arrayOf { unbox (arrayType (name())) }
  , m_source { std::move (source_) }
{
    DBG ("ARRAY OF::" << arrayOf() << ", name::" << name() <<  std::endl);
}

/******************************************************************************/

std::vector<std::string>::const_iterator
amqp::internal::schema::
Array::begin() const {
    return m_arrayOf.begin();
}

/******************************************************************************/

std::vector<std::string>::const_iterator
amqp::internal::schema::
Array::end() const {
    return m_arrayOf.end();
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
Array::arrayOf() const {
    return m_arrayOf[0];
}

/******************************************************************************/

int
amqp::internal::schema::
Array::dependsOnMap (const amqp::internal::schema::Map & map_) const {
    // do we depend on the lhs
    if (arrayOf() == map_.name()) {
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
Array::dependsOnList (const amqp::internal::schema::List & list_) const {
    // do we depend on the lhs
    if (arrayOf() == list_.name()) {
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
Array::dependsOnArray (const amqp::internal::schema::Array & array_) const {
    return 0;
}

/******************************************************************************/

int
amqp::internal::schema::
Array::dependsOnEnum (const amqp::internal::schema::Enum & enum_) const {
    // an enum cannot depend on us so don't bother checking, lets just check
    // if we depend on it
    if (arrayOf() == enum_.name()) {
        return 1;
    }

    return 0;
}

/******************************************************************************/

int
amqp::internal::schema::
Array::dependsOnRHS (const amqp::internal::schema::Composite & lhs_) const {
    if (arrayOf() == lhs_.name()) {
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
