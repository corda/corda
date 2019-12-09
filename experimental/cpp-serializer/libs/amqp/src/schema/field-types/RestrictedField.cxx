#include "RestrictedField.h"

/******************************************************************************/

const std::string
amqp::internal::schema::RestrictedField::m_fieldType {
    "restricted"
};

/******************************************************************************/

amqp::internal::schema::
RestrictedField::RestrictedField (
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
}

/******************************************************************************/

bool
amqp::internal::schema::
RestrictedField::primitive() const {
    return false;
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
RestrictedField::fieldType() const {
    return m_fieldType;
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
RestrictedField::resolvedType() const {
    return requires().front();
}

/******************************************************************************/
