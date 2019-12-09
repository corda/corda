#include "PrimitiveField.h"

/******************************************************************************/

const std::string
amqp::internal::schema::PrimitiveField::m_fieldType {
        "primitive"
};

/******************************************************************************/

amqp::internal::schema::
PrimitiveField::PrimitiveField (
    std::string name_,
    std::string type_,
    std::string default_,
    std::string label_,
    bool mandatory_,
    bool multiple_
) : Field (
    std::move (name_),
    std::move (type_),
    { }, // requires
    std::move (default_),
    std::move (label_),
    mandatory_,
    multiple_
) {
}

/******************************************************************************/

bool
amqp::internal::schema::
PrimitiveField::primitive() const {
    return true;
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
PrimitiveField::fieldType() const {
    return m_fieldType;
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
PrimitiveField::resolvedType() const {
    return type();
}

/******************************************************************************/
