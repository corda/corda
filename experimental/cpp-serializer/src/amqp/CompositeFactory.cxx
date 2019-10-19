#include "CompositeFactory.h"

#include <set>
#include <vector>
#include <iostream>
#include <algorithm>
#include <functional>

#include <assert.h>

#include "debug.h"

#include "amqp/reader/IReader.h"
#include "amqp/reader/PropertyReader.h"

#include "reader/Reader.h"
#include "reader/CompositeReader.h"
#include "reader/RestrictedReader.h"
#include "reader/restricted-readers/MapReader.h"
#include "reader/restricted-readers/ListReader.h"
#include "reader/restricted-readers/ArrayReader.h"
#include "reader/restricted-readers/EnumReader.h"

#include "schema/restricted-types/Map.h"
#include "schema/restricted-types/List.h"
#include "schema/restricted-types/Enum.h"
#include "schema/restricted-types/Array.h"

/******************************************************************************/

namespace {

/**
 *
 */
    template<typename T>
    std::shared_ptr<T> &
    computeIfAbsent(
            spStrMap_t<T> &map_,
            const std::string &k_,
            std::function<std::shared_ptr<T>(void)> f_
    ) {
        auto it = map_.find(k_);

        if (it == map_.end()) {
            DBG ("ComputeIfAbsent \"" << k_ << "\" - missing" << std::endl); // NOLINT
            map_[k_] = std::move (f_());
            DBG ("                \"" << k_ << "\" - RTN: " << map_[k_]->name() << " : " << map_[k_]->type()
                                      << std::endl); // NOLINT
            assert (map_[k_]);
            assert (map_[k_] != nullptr);
            DBG (k_ << " =?= " << map_[k_]->type() << std::endl);
            assert (k_ == map_[k_]->type());

            return map_[k_];
        } else {
            DBG ("ComputeIfAbsent \"" << k_ << "\" - found it" << std::endl); // NOLINT
            DBG ("                \"" << k_ << "\" - RTN: " << map_[k_]->name() << std::endl); // NOLINT

            assert (it->second != nullptr);

            return it->second;
        }
    }

}

/******************************************************************************
 *
 *  CompositeFactory
 *
 ******************************************************************************/

/**
 *
 * Walk through the types in a Schema and produce readers for them.
 *
 * We are making the assumption that the contents of [schema_]
 * are strictly ordered by dependency so we can construct types
 * as we go without needing to provide look ahead for types
 * we haven't built yet.
 *
 */
void
amqp::internal::
CompositeFactory::process (const SchemaType & schema_) {
    DBG ("process schema" << std::endl);

    for (const auto & i : dynamic_cast<const schema::Schema &>(schema_)) {
        for (const auto & j : i) {
            process (*j);
            m_readersByDescriptor[j->descriptor()] = m_readersByType[j->name()];
        }
    }
}

/******************************************************************************/

std::shared_ptr<amqp::internal::reader::Reader>
amqp::internal::
CompositeFactory::process (
    const amqp::internal::schema::AMQPTypeNotation & schema_)
{
    DBG ("process::" << schema_.name() << std::endl);

    return computeIfAbsent<reader::Reader> (
        m_readersByType,
        schema_.name(),
        [& schema_, this] () -> std::shared_ptr<reader::Reader> {
            switch (schema_.type()) {
                case schema::AMQPTypeNotation::composite_t : {
                    return processComposite (schema_);
                }
                case schema::AMQPTypeNotation::restricted_t : {
                    return processRestricted (schema_);
                }
            }
        });
}

/******************************************************************************/

std::shared_ptr<amqp::internal::reader::Reader>
amqp::internal::
CompositeFactory::processComposite (
        const amqp::internal::schema::AMQPTypeNotation & type_
) {
    DBG ("processComposite - " << type_.name() << std::endl);
    std::vector<std::weak_ptr<reader::Reader>> readers;

    const auto & fields = dynamic_cast<const schema::Composite &> (
            type_).fields();

    readers.reserve (fields.size());

    for (const auto & field : fields) {
        DBG ("  Field: " << field->name() << ": \"" << field->type()
            << "\" {" << field->resolvedType() << "} "
            << field->fieldType() << std::endl); // NOLINT

       decltype (m_readersByType)::mapped_type reader;

        if (field->primitive()) {
            reader = computeIfAbsent<reader::Reader> (
                    m_readersByType,
                    field->resolvedType(),
                    [&field]() -> std::shared_ptr<reader::PropertyReader> {
                        return reader::PropertyReader::make (field);
                    });
        }
        else {
            // Insertion sorting ensures any type we depend on will have
            // already been created and thus exist in the map
            reader = m_readersByType[field->resolvedType()];
        }


        assert (reader);
        readers.emplace_back (reader);
        assert (readers.back().lock());
    }

    return std::make_shared<reader::CompositeReader> (type_.name(), readers);
}

/******************************************************************************/

std::shared_ptr<amqp::internal::reader::Reader>
amqp::internal::
CompositeFactory::processEnum (
    const amqp::internal::schema::Enum & enum_
) {
    DBG ("Processing Enum - " << enum_.name() << std::endl); // NOLINT

    return std::make_shared<reader::EnumReader> (
        enum_.name(),
        enum_.makeChoices());
}

/******************************************************************************/

std::shared_ptr<amqp::internal::reader::Reader>
amqp::internal::
CompositeFactory::fetchReaderForRestricted (const std::string & type_) {
    decltype(m_readersByType)::mapped_type rtn;

    DBG ("fetchReaderForRestricted - " << type_ << std::endl);

    if (schema::Field::typeIsPrimitive(type_)) {
        DBG ("It's primitive" << std::endl);
        rtn = computeIfAbsent<reader::Reader>(
                m_readersByType,
                type_,
                [& type_]() -> std::shared_ptr<reader::PropertyReader> {
                    return reader::PropertyReader::make (type_);
                });
    } else {
        rtn = m_readersByType[type_];
    }

    if (!rtn) {
        throw std::runtime_error ("Missing type in map");
    }

    return rtn;
}

/******************************************************************************/

std::shared_ptr<amqp::internal::reader::Reader>
amqp::internal::
CompositeFactory::processMap (
    const amqp::internal::schema::Map & map_
) {
    DBG ("Processing Map - "
        << map_.mapOf().first.get() << " "
        << map_.mapOf().second.get() << std::endl); // NOLINT

    const auto types = map_.mapOf();

    return std::make_shared<reader::MapReader> (
            map_.name(),
            fetchReaderForRestricted (types.first),
            fetchReaderForRestricted (types.second));
}

/******************************************************************************/

std::shared_ptr<amqp::internal::reader::Reader>
amqp::internal::
CompositeFactory::processList (
    const amqp::internal::schema::List & list_
) {
    DBG ("Processing List - " << list_.listOf() << std::endl); // NOLINT

    return std::make_shared<reader::ListReader> (
            list_.name(),
            fetchReaderForRestricted (list_.listOf()));
}

/******************************************************************************/

std::shared_ptr<amqp::internal::reader::Reader>
amqp::internal::
CompositeFactory::processArray (
        const amqp::internal::schema::Array & array_
) {
    DBG ("Processing Array - " << array_.name() << " " << array_.arrayOf() << std::endl); // NOLINT

    return std::make_shared<reader::ArrayReader> (
            array_.name(),
            fetchReaderForRestricted (array_.arrayOf()));
}

/******************************************************************************/

std::shared_ptr<amqp::internal::reader::Reader>
amqp::internal::
CompositeFactory::processRestricted (
        const amqp::internal::schema::AMQPTypeNotation & type_)
{
    DBG ("processRestricted - " << type_.name() << std::endl); // NOLINT
    const auto & restricted = dynamic_cast<const schema::Restricted &> (
            type_);

    switch (restricted.restrictedType()) {
        case schema::Restricted::RestrictedTypes::list_t : {
            return processList (
                dynamic_cast<const schema::List &> (restricted));
        }
        case schema::Restricted::RestrictedTypes::enum_t : {
            return processEnum (
                dynamic_cast<const schema::Enum &> (restricted));
        }
        case schema::Restricted::RestrictedTypes::map_t : {
            return processMap (
                dynamic_cast<const schema::Map &> (restricted));
        }
        case schema::Restricted::RestrictedTypes::array_t : {
            DBG ("  array_t" << std::endl);
            return processArray (
                dynamic_cast<const schema::Array &> (restricted));
        }
    }

    DBG ("  ProcessRestricted: Returning nullptr"); // NOLINT
    return nullptr;
}

/******************************************************************************/

const std::shared_ptr<amqp::internal::reader::IReader>
amqp::internal::
CompositeFactory::byType (const std::string & type_) {
    auto it = m_readersByType.find (type_);

    return (it == m_readersByType.end()) ? nullptr : it->second;
}

/******************************************************************************/

const std::shared_ptr<amqp::internal::reader::IReader>
amqp::internal::
CompositeFactory::byDescriptor (const std::string & descriptor_) {
    auto it = m_readersByDescriptor.find (descriptor_);

    return (it == m_readersByDescriptor.end()) ? nullptr : it->second;
}

/******************************************************************************/
