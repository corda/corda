#include "CompositeField.h"

#include <iostream>

#include "Field.h"

#include "debug.h"

/******************************************************************************/

const std::string
amqp::internal::schema::CompositeField::m_fieldType {
        "composite"
};

/******************************************************************************/

amqp::internal::schema::
CompositeField::CompositeField (
        std::string name_,
        std::string type_,
        std::list<std::string> requires_,
        std::string default_,
        std::string label_,
        bool mandatory_,
        bool multiple_
) : Field (
        std::move (name_),
        std::move (type_),
        std::move (requires_),
        std::move (default_),
        std::move (label_),
        mandatory_,
        multiple_)
{
    DBG ("FIELD::FIELD - name: " << name() << ", type: " << type() << std::endl);
}

/******************************************************************************/

bool
amqp::internal::schema::
CompositeField::primitive() const {
    return false;
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
CompositeField::fieldType() const {
    return m_fieldType;
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
CompositeField::resolvedType() const {
    return type();
}

/******************************************************************************/
