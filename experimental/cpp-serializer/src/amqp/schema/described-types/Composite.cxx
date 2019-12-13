#include "Composite.h"

#include "debug.h"
#include "colours.h"

#include "amqp/schema/restricted-types/Restricted.h"

#include <iomanip>
#include <iostream>

/******************************************************************************/

namespace amqp::internal::schema {

    std::ostream &
    operator << (std::ostream & stream_, const Composite & clazz_) {
        stream_
            << "name       : " << clazz_.name() << std::endl
            << "label      : " << clazz_.m_label << std::endl
            << "descriptor : " << clazz_.descriptor() << std::endl
            << "fields     : ";

        for (auto const & i : clazz_.m_fields) stream_ << *i << std::setw (13) << " ";
        stream_ << std::setw(0);

        return stream_;
    }

}

/******************************************************************************
 *
 * amqp::internal::schema::Composite
 *
 ******************************************************************************/

amqp::internal::schema::
Composite::Composite (
        std::string name_,
        std::string label_,
        sList<std::string> provides_,
        uPtr<Descriptor> descriptor_,
        std::vector<uPtr<Field>> fields_
) : AMQPTypeNotation (
        std::move (name_),
        std::move (descriptor_))
  , m_label (std::move (label_))
  , m_provides (std::move (provides_))
  , m_fields (std::move (fields_))
{ }

/******************************************************************************/

const std::vector<uPtr<amqp::internal::schema::Field>> &
amqp::internal::schema::
Composite::fields() const {
    return m_fields;
}

/******************************************************************************/

amqp::internal::schema::AMQPTypeNotation::Type
amqp::internal::schema::
Composite::type() const {
    return AMQPTypeNotation::Type::composite_t;
}

/******************************************************************************/

/**
 * Use a visitor style pattern to work out weather two types, composite or
 * restricted, are "less than" one or not. In this case we define being
 * "less than" not having a type that the other depends on. This will
 * eventually give us a set ordered in such a way we can simply create
 * each element in turn
 *
 * "...This object determines the order of the elements in the container: it is
 * a function pointer or a function object that takes two arguments of the same
 * type as the container elements, and returns true if the first argument is
 * considered to go before the second in the strict weak ordering it defines,
 * and false otherwise. ..."

 *
 * @param rhs
 * @return
 */
int
amqp::internal::schema::
Composite::dependsOn (const OrderedTypeNotation & rhs) const {
    return dynamic_cast<const AMQPTypeNotation &>(rhs).dependsOnRHS(*this);
}

/******************************************************************************/

int
amqp::internal::schema::
Composite::dependsOnRHS (
        const amqp::internal::schema::Restricted & lhs_
) const {
    // does this depend on the left hand side
    for (auto const & field : m_fields) {
        DBG ("  C/R b) " << field->resolvedType() << " == " << lhs_.name() << std::endl); // NOLINT

        if (field->resolvedType() == lhs_.name()) {
            return 1;
        }
    }

    // does the left hand side depend on us
    for (const auto i : lhs_) {
        DBG ("  C/R a) " << i << " == " << name() << std::endl); // NOLINT
        if (i == name()) {
            return 2;
        }
    }

    return 0;
}

/*********************************************************o*********************/

int
amqp::internal::schema::
Composite::dependsOnRHS (
        const amqp::internal::schema::Composite & lhs_
) const {
    DBG (name() << " ?Depends on " << lhs_.name() << std::endl);

    // do we depend on the lhs
    for (const auto & field : m_fields) {
        DBG ("FIELD - " << name() << "::" << type() << std::endl);
        DBG ("  C/C a) " << field->resolvedType() << " == " << lhs_.name() << std::endl); // NOLINT

        if (field->resolvedType() == lhs_.name()) {
            return 1;
        }
    }

    // does it depend on us
    for (auto const & field : lhs_) {
        DBG ("FIELD - " << name() << "::" << type() << std::endl);
        DBG ("  C/C b) " << field->resolvedType() << " == " << name() << std::endl); // NOLINT
        if (field->resolvedType() == name()) {
            return 2;
        }
    }


    return 0;
}

/******************************************************************************/
