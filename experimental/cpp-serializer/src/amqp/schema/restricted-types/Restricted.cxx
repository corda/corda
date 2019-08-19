#include "Restricted.h"
#include "List.h"

#include <string>
#include <vector>
#include <iostream>

/******************************************************************************/

namespace amqp::internal::schema {

std::ostream &
operator << (
    std::ostream & stream_,
    const amqp::internal::schema::Restricted & clazz_)
{
    stream_
        << "name       : " << clazz_.name() << std::endl
        << "label      : " << clazz_.m_label << std::endl
        << "descriptor : " << clazz_.descriptor() << std::endl
        << "source     : " << clazz_.m_source << std::endl
        << "provides   : [" << std::endl;

    for (auto & provides : clazz_.m_provides) {
        stream_ << "              " << provides << std::endl;
    }
    stream_<< "             ]" << std::endl;

    return stream_;
}

}

/******************************************************************************/

namespace amqp::internal::schema {

std::ostream &
operator << (
    std::ostream & stream_,
    const amqp::internal::schema::Restricted::RestrictedTypes & type_)
{
    switch (type_) {
        case Restricted::RestrictedTypes::List : {
            stream_ << "list";
            break;
        }
        case Restricted::RestrictedTypes::Map : {
            stream_ << "map";
            break;
        }
    }

    return stream_;
}

}

/******************************************************************************
 *
 * amqp::internal::schema::Restricted
 *
 ******************************************************************************/

/**
 * Named constructor
 *
 * @param descriptor_
 * @param name_
 * @param label_
 * @param provides_
 * @param source_
 * @return
 */
std::unique_ptr<amqp::internal::schema::Restricted>
amqp::internal::schema::
Restricted::make(
        uPtr<Descriptor> & descriptor_,
        const std::string & name_,
        const std::string & label_,
        const std::vector<std::string> & provides_,
        const std::string & source_)
{
    if (source_ == "list") {
        return std::make_unique<amqp::internal::schema::List> (
                descriptor_, name_, label_, provides_, source_);
    }
}

/******************************************************************************/

amqp::internal::schema::
Restricted::Restricted (
    uPtr<Descriptor> & descriptor_,
    const std::string & name_,
    std::string label_,
    const std::vector<std::string> & provides_,
    const amqp::internal::schema::Restricted::RestrictedTypes & source_
) : AMQPTypeNotation (name_, descriptor_)
  , m_label (std::move (label_))
  , m_provides (provides_)
  , m_source (source_)
{
}

/******************************************************************************/

amqp::internal::schema::AMQPTypeNotation::Type
amqp::internal::schema::
Restricted::type() const {
    return AMQPTypeNotation::Type::Restricted;
}

/******************************************************************************/

amqp::internal::schema::Restricted::RestrictedTypes
amqp::internal::schema::
Restricted::restrictedType() const {
    return m_source;
}

/******************************************************************************/

int
amqp::internal::schema::
Restricted::dependsOn (const OrderedTypeNotation & rhs_) const {
    return dynamic_cast<const AMQPTypeNotation &>(rhs_).dependsOn(*this);
}

/*********************************************************o*********************/

