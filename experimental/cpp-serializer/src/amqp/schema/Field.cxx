#include "Field.h"

#include <sstream>
#include <iostream>

/******************************************************************************/

namespace amqp::internal::schema {

std::ostream &
operator << (std::ostream & stream_, const Field & field_) {
    std::stringstream ss;
    for (auto &i: field_.m_requires) { ss << i; }

    stream_ << field_.m_name << " : " << field_.m_type.first << " : [" << ss.str() << "]" << std::endl;

    return stream_;
}

}

/******************************************************************************/

amqp::internal::schema::
Field::Field (
    const std::string & name_,
    const std::string & type_,
    const std::list<std::string> & requires_,
    const std::string & default_,
    const std::string & label_,
    bool mandatory_,
    bool multiple_
) : m_name (name_)
  , m_requires (requires_)
  , m_default (default_)
  , m_label (label_)
  , m_mandatory (mandatory_)
  , m_multiple (multiple_)
{
    if (typeIsPrimitive(type_)) {
        m_type = std::make_pair(type_, FieldType::PrimitiveProperty);
    } else if (type_ == "*") {
        m_type = std::make_pair(type_, FieldType::RestrictedProperty);
    } else {
        m_type = std::make_pair(type_, FieldType::CompositeProperty);
    }
}

/******************************************************************************/

bool
amqp::internal::schema::
Field::typeIsPrimitive(const std::string & type_) {
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
    return m_type.first;
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
Field::resolvedType() const {
    return (type() == "*") ? requires().front() : type();
}

/******************************************************************************/

amqp::internal::schema::FieldType
amqp::internal::schema::
Field::fieldType() const {
    return m_type.second;
}

/******************************************************************************/

const std::list<std::string> &
amqp::internal::schema::
Field::requires() const {
    return m_requires;
}

/******************************************************************************/

bool
amqp::internal::schema::
Field::primitive() const {
    return m_type.second == PrimitiveProperty;
}

/******************************************************************************/
