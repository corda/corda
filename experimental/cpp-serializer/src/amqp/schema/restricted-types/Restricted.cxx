#include "Restricted.h"
#include "List.h"
#include "Enum.h"

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
        case Restricted::RestrictedTypes::Enum : {
            stream_ << "enum";
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
        uPtr<Descriptor> descriptor_,
        std::string name_,
        std::string label_,
        std::vector<std::string> provides_,
        std::string source_,
        std::vector<uPtr<Choice>> choices_)
{
    /*
     * Lists represent both actual lists and enumerations. We differentiate
     * between them as enums have choices ans lists don't. Pretty certain
     * things are done this was as AMQP doesn't really have the concept
     * of an enum.
     */
    if (source_ == "list") {
        if (choices_.empty()) {
            return std::make_unique<amqp::internal::schema::List>(
                    std::move (descriptor_),
                    std::move (name_),
                    std::move (label_),
                    std::move (provides_),
                    std::move (source_));
        } else {
            return std::make_unique<amqp::internal::schema::Enum>(
                    std::move (descriptor_),
                    std::move (name_),
                    std::move (label_),
                    std::move (provides_),
                    std::move (source_),
                    std::move (choices_));
        }
    } else if (source_ == "map") {
        throw std::runtime_error ("maps not supported");
    }
}

/******************************************************************************/

amqp::internal::schema::
Restricted::Restricted (
    uPtr<Descriptor> descriptor_,
    std::string name_,
    std::string label_,
    std::vector<std::string> provides_,
    amqp::internal::schema::Restricted::RestrictedTypes source_
) : AMQPTypeNotation (
        std::move (name_),
        std::move (descriptor_))
  , m_label (std::move (label_))
  , m_provides (std::move (provides_))
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

