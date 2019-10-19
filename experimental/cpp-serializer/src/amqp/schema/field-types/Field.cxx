#include "Field.h"

#include <sstream>
#include <iostream>

#include "debug.h"

#include "ArrayField.h"
#include "PrimitiveField.h"
#include "CompositeField.h"
#include "RestrictedField.h"

#include "../restricted-types/Array.h"

/******************************************************************************/

namespace amqp::internal::schema {

std::ostream &
operator << (std::ostream & stream_, const Field & field_) {
    std::stringstream ss;
    for (auto &i: field_.m_requires) { ss << i; }

    stream_ << field_.m_name
        << " : " << field_.m_type
        << " : [" << ss.str() << "]" << std::endl;

    return stream_;
}

}

/******************************************************************************/

uPtr<amqp::internal::schema::Field>
amqp::internal::schema::
Field::make (
        std::string name_,
        std::string type_,
        std::list<std::string> requires_,
        std::string default_,
        std::string label_,
        bool mandatory_,
        bool multiple_
) {
    if (typeIsPrimitive (type_)) {
        DBG ("-> primitive" << std::endl);
        return std::make_unique<PrimitiveField>(
                std::move (name_),
                std::move (type_),
                std::move (default_),
                std::move (label_),
                mandatory_,
                multiple_);
    } else if (Array::isArrayType (type_)) {
        DBG ("-> array" << std::endl);
        return std::make_unique<ArrayField>(
                std::move (name_),
                std::move (type_),
                std::move (requires_),
                std::move (default_),
                std::move (label_),
                mandatory_,
                multiple_);

    } else if (type_ == "*") {
        DBG ("-> restricted" << std::endl);
        return std::make_unique<RestrictedField>(
                std::move (name_),
                std::move (type_),
                std::move (requires_),
                std::move (default_),
                std::move (label_),
                mandatory_,
                multiple_);
    } else {
        DBG ("-> composite" << std::endl);
        return std::make_unique<CompositeField>(
                std::move (name_),
                std::move (type_),
                std::move (requires_),
                std::move (default_),
                std::move (label_),
                mandatory_,
                multiple_);
    }

}

/******************************************************************************
 *
 *
 *
 ******************************************************************************/

amqp::internal::schema::
Field::Field (
    std::string name_,
    std::string type_,
    std::list<std::string> requires_,
    std::string default_,
    std::string label_,
    bool mandatory_,
    bool multiple_
) : m_name (std::move (name_))
  , m_type (std::move (type_))
  , m_requires (std::move (requires_))
  , m_default (std::move (default_))
  , m_label (std::move (label_))
  , m_mandatory (mandatory_)
  , m_multiple (multiple_)
{
    DBG ("FIELD::FIELD - name: " << name() << ", type: " << type_ << std::endl);
}

/******************************************************************************/

bool
amqp::internal::schema::
Field::typeIsPrimitive (const std::string & type_) {
    return (type_ == "string" ||
            type_ == "long" ||
            type_ == "boolean" ||
            type_ == "int" ||
            type_ == "double");
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
Field::name() const {
    return m_name;
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
Field::type() const {
    return m_type;
}

/******************************************************************************/

const std::list<std::string> &
amqp::internal::schema::
Field::requires() const {
    return m_requires;
}

/******************************************************************************/

