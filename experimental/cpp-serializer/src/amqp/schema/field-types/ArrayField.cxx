#include "ArrayField.h"

#include "debug.h"

#include <iostream>

/******************************************************************************/

const std::string
amqp::internal::schema::
ArrayField::m_fieldType {
        "array"
};

/******************************************************************************/

amqp::internal::schema::
ArrayField::ArrayField (
    std::string name_,
    std::string type_,
    std::list<std::string> requires_,
    std::string default_,
    std::string label_,
    bool mandatory_,
    bool multiple_
) : RestrictedField (
        std::move (name_),
        std::move (type_),
        std::move (requires_),
        std::move (default_),
        std::move (label_),
        mandatory_,
        multiple_
) {
    DBG ("ArrayField::ArrayField - name: " << name() << ", type: " << type() << std::endl);
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
ArrayField::resolvedType() const {
    return type();
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
ArrayField::fieldType() const {
    return m_fieldType;
}

/******************************************************************************/
