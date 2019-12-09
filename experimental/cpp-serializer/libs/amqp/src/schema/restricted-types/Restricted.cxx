#include "Restricted.h"

#include "Map.h"
#include "List.h"
#include "Enum.h"
#include "Array.h"

#include <string>
#include <vector>
#include <iostream>

/******************************************************************************
 *
 * ostream overloads
 *
 ******************************************************************************/

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
            case Restricted::RestrictedTypes::list_t : {
                stream_ << "list";
                break;
            }
            case Restricted::RestrictedTypes::map_t : {
                stream_ << "map";
                break;
            }
            case Restricted::RestrictedTypes::enum_t : {
                stream_ << "enum";
                break;
            }
            case Restricted::RestrictedTypes::array_t : {
                stream_ << "array";
                break;
            }
        }

        return stream_;
    }

}

/******************************************************************************
 *
 * Static member functions
 *
 ******************************************************************************/

namespace {

    std::map<std::string, std::string> boxedToUnboxed = {
            { "java.lang.Integer", "int" },
            { "java.lang.Boolean", "bool" },
            { "java.lang.Byte", "char" },
            { "java.lang.Short", "short" },
            { "java.lang.Character", "char" },
            { "java.lang.Float", "float" },
            { "java.lang.Long", "long" },
            { "java.lang.Double", "double" }
    };

}

/******************************************************************************/

/**
 * Java gas two types of primitive, boxed and unboxed, essentially actual
 * primitives and classes representing those primitives. Of course, we
 * don't care about that, so treat boxed primitives as their underlying
 * type.
 */
std::string
amqp::internal::schema::
Restricted::unbox (const std::string & type_) {
    auto it = boxedToUnboxed.find (type_);
    if (it == boxedToUnboxed.end()) return type_;
    else return it->second;
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
uPtr<amqp::internal::schema::Restricted>
amqp::internal::schema::
Restricted::make(
        uPtr<Descriptor> descriptor_,
        std::string name_,
        std::string label_,
        std::vector<std::string> provides_,
        std::string source_,
        std::vector<uPtr<Choice>> choices_)
{
    DBG ("RESTRICTED::MAKE - " << name_ << std::endl);
    /*
     * AMQP Lists represent actual lists, arrays, and enumerations.
     *
     * Enumerations are  serialised as lists that have a set of Choices
     * Arrays are serialized as lists of types. Because java cares about the difference between
     * boxed and un-boxed primitives an unboxed array ends with [p] whilst an array of classes
     * ends with []
     */
    if (source_ == "list") {
        if (choices_.empty()) {
            const std::string array { "[]" };
            const std::string primArray { "[p]" };

            // when C++20 is done we can use .endswith, until then we have to do a reverse search
            if (   std::equal (name_.rbegin(), name_.rbegin() + array.size(), array.rbegin(), array.rend())
                || std::equal (name_.rbegin(), name_.rbegin() + primArray.size(), primArray.rbegin(), primArray.rend()))
            {
                return std::make_unique<Array>(
                        std::move (descriptor_),
                        std::move (name_),
                        std::move (label_),
                        std::move (provides_),
                        std::move (source_));
            } else {
                return std::make_unique<List>(
                        std::move (descriptor_),
                        std::move (name_),
                        std::move (label_),
                        std::move (provides_),
                        std::move (source_));
            }
        } else {
            return std::make_unique<Enum>(
                    std::move (descriptor_),
                    std::move (name_),
                    std::move (label_),
                    std::move (provides_),
                    std::move (source_),
                    std::move (choices_));
        }
    } else if (source_ == "map") {
        return std::make_unique<Map> (
                std::move (descriptor_),
                std::move (name_),
                std::move (label_),
                std::move (provides_),
                std::move (source_));
    } else {
        throw std::runtime_error ("Unknown restricted type");
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
  , m_label { std::move (label_) }
  , m_provides { std::move (provides_) }
  , m_source { source_ }
{
}

/******************************************************************************/

amqp::internal::schema::AMQPTypeNotation::Type
amqp::internal::schema::
Restricted::type() const {
    return AMQPTypeNotation::Type::restricted_t;
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
    return dynamic_cast<const AMQPTypeNotation &>(rhs_).dependsOnRHS (*this);
}

/*********************************************************o*********************/

/*
 * If the left hand side of the original call, restricted_ in this case,
 * depends on this instance then we return 1.
 *
 * If this instance of a map depends on the parameter we return 2
 */
int
amqp::internal::schema::
Restricted::dependsOnRHS (const Restricted & lhs_) const  {
    switch (lhs_.restrictedType()) {
        case Restricted::RestrictedTypes::map_t :
            return dependsOnMap (
                static_cast<const amqp::internal::schema::Map &>(lhs_)); // NOLINT
        case Restricted::RestrictedTypes::list_t :
            return dependsOnList (
                static_cast<const amqp::internal::schema::List &>(lhs_)); // NOLINT
        case Restricted::RestrictedTypes::enum_t :
            return dependsOnEnum (
                static_cast<const amqp::internal::schema::Enum &>(lhs_)); // NOLINT
        case Restricted::RestrictedTypes::array_t :
            return dependsOnArray (
                    static_cast<const amqp::internal::schema::Array &>(lhs_)); // NOLINT
    }
}

/*********************************************************o*********************/
