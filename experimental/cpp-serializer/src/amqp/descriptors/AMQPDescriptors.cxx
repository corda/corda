#include "AMQPDescriptors.h"
#include "AMQPDescriptorRegistory.h"

#include <string>
#include <iostream>
#include <proton/types.h>
#include <proton/codec.h>
#include "colours.h"

#include "debug.h"
#include "Field.h"
#include "Schema.h"
#include "Envelope.h"
#include "Composite.h"
#include "amqp/schema/restricted-types/Restricted.h"
#include "amqp/schema/OrderedTypeNotations.h"
#include "amqp/AMQPDescribed.h"

#include "proton/proton_wrapper.h"
#include "AMQPDescriptorRegistory.h"

/******************************************************************************
 *
 *
 *
 ******************************************************************************/

namespace {

    /**
     * Look up a described type by its ID in the AMQPDescriptorRegistry and
     * return the corresponding schema type. Specialised below to avoid
     * the cast and re-owning of the unigue pointer when we're happy
     * with a simple uPtr<AMQPDescribed>
     */
    template<class T>
    uPtr<T>
    dispatchDescribed (pn_data_t * data_) {
        proton::is_described(data_);
        proton::auto_enter p (data_);
        proton::is_ulong(data_);

        auto id = pn_data_get_ulong(data_);

        return uPtr<T> (
              static_cast<T *>(amqp::AMQPDescriptorRegistory[id]->build(data_).release()));
    }

}

/******************************************************************************/

void
amqp::internal::
AMQPDescriptor::validateAndNext (pn_data_t * const data_) const {
    if (pn_data_type(data_) != PN_ULONG) {
        throw std::runtime_error ("Bad type for a descriptor");
    }

    if (   (m_val == -1)
        || (pn_data_get_ulong(data_) != (static_cast<uint32_t>(m_val) | amqp::internal::DESCRIPTOR_TOP_32BITS)))
    {
        throw std::runtime_error ("Invalid Type");
    }

    pn_data_next(data_);
}

/******************************************************************************/

namespace {
    const std::string
    consumeBlob (pn_data_t * data_) {
        proton::is_described (data_);
        proton::auto_enter p (data_);
        return proton::get_symbol<std::string> (data_);
    }
}

/******************************************************************************
 *
 * amqp::internal::EnvelopeDescriptor
 *
 ******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
EnvelopeDescriptor::build(pn_data_t * data_) const {
    DBG ("ENVELOPE" << std::endl); // NOLINT

    validateAndNext(data_);

    proton::auto_enter p (data_);

    /*
     * The actual blob... if this was java we would use the type symbols
     * in the blob to look up serialisers in the cache... but we don't 
     * have any so we are actually going to need to use the schema
     * which we parse *after* this to be able to read any data!
     */
    std::string outerType = consumeBlob(data_);

    pn_data_next (data_);

    /*
     * The schema
     */
    auto schema = dispatchDescribed<schema::Schema> (data_);

    pn_data_next(data_);

    /*
     * The transforms schema
     */
    // Skip for now
    // dispatchDescribed (data_);

    return std::make_unique<schema::Envelope> (schema::Envelope (schema, outerType));
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
SchemaDescriptor::build(pn_data_t * data_) const {
    DBG ("SCHEMA" << std::endl); // NOLINT

    validateAndNext(data_);

    schema::OrderedTypeNotations<schema::AMQPTypeNotation> schemas;

    /*
     * The Schema is stored as a list of lists of described objects
     */
    {
        proton::auto_list_enter ale (data_);

        for (int i { 1 } ; pn_data_next(data_) ; ++i) {
            DBG ("  " << i << "/" << ale.elements() <<  std::endl); // NOLINT
            proton::auto_list_enter ale2 (data_);
            while (pn_data_next(data_)) {
                schemas.insert (dispatchDescribed<schema::AMQPTypeNotation>(data_));
            }
        }
    }

    return std::make_unique<schema::Schema> (std::move (schemas));
}

/******************************************************************************
 *
 * amqp::internal::ObjectDescriptor
 *
 ******************************************************************************/

/**
 * 
 */
uPtr<amqp::AMQPDescribed>
amqp::internal::
ObjectDescriptor::build(pn_data_t * data_) const {
    DBG ("DESCRIPTOR" << std::endl); // NOLINT

    validateAndNext(data_);

    proton::auto_enter p (data_);

    auto symbol = proton::get_symbol<std::string> (data_);

    return std::make_unique<schema::Descriptor> (symbol);
}

/******************************************************************************
 *
 * amqp::internal::FieldDescriptor
 *
 ******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
FieldDescriptor::build(pn_data_t * data_) const {
    DBG ("FIELD" << std::endl); // NOLINT

    validateAndNext(data_);

    proton::auto_enter ae (data_);

    /* name: String */
    auto name = proton::get_string (data_);

    pn_data_next(data_);

    /* type: String */
    auto type = proton::get_string (data_);

    pn_data_next(data_);

    /* requires: List<String> */
    std::list<std::string> requires;
    {
        proton::auto_list_enter ale (data_);
        while (pn_data_next(data_)) {
            requires.push_back (proton::get_string(data_));
        }
    }

    pn_data_next(data_);

    /* default: String? */
    auto def = proton::get_string (data_, true);

    pn_data_next(data_);

    /* label: String? */
    auto label = proton::get_string (data_, true);

    pn_data_next(data_);

    /* mandatory: Boolean - copes with the Kotlin concept of nullability.
       If something is mandatory then it cannot be null */
    auto mandatory = proton::get_boolean (data_);

    pn_data_next(data_);

    /* multiple: Boolean */
    auto multiple = proton::get_boolean(data_);

    return std::make_unique<schema::Field> (name, type, requires, def, label,
            mandatory, multiple);
}

/******************************************************************************
 *
 * amqp::internal::CompositeDescriptor
 *
 ******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
CompositeDescriptor::build (pn_data_t * data_) const {
    DBG ("COMPOSITE" << std::endl); // NOLINT

    validateAndNext(data_);

    proton::auto_enter p (data_);

    /* Class Name - String */
    auto name = proton::get_string(data_);

    pn_data_next(data_);

    /* Label Name - Nullable String */
    auto label = proton::get_string (data_, true);

    pn_data_next(data_);

    /* provides: List<String> */
    std::list<std::string> provides;
    {
        proton::auto_list_enter p2 (data_);
        while (pn_data_next(data_)) {
            provides.push_back (proton::get_string (data_));
        }
    }

    pn_data_next(data_);

    /* descriptor: Descriptor */
    auto descriptor = dispatchDescribed<schema::Descriptor>(data_);

    pn_data_next(data_);

    /* fields: List<Described>*/
    std::vector<uPtr<schema::Field>> fields;
    fields.reserve (pn_data_get_list (data_));
    {
        proton::auto_list_enter p2 (data_);
        while (pn_data_next (data_)) {
            fields.emplace_back (dispatchDescribed<schema::Field>(data_));
        }
    }

    return std::make_unique<schema::Composite> (
        schema::Composite (name, label, provides, descriptor, fields));
}

/******************************************************************************
 *
 * Restricted types represent lists and maps
 *
 * NOTE: The Corda serialization scheme doesn't support all container classes
 * as it has the requiremnt that iteration order be deterministic for purposes
 * of signing over data.
 *
 *      name : String
 *      label : String?
 *      provides : List<String>
 *      source : String
 *      descriptor : Descriptor
 *      choices : List<Choice>
 *
 ******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
RestrictedDescriptor::build (pn_data_t * data_) const {
    DBG ("RESTRICTED" << std::endl); // NOLINT
    validateAndNext(data_);

    proton::auto_enter ae (data_);

    auto name  = proton::readAndNext<std::string>(data_);
    auto label = proton::readAndNext<std::string>(data_, true);

    std::vector<std::string> provides;
    {
        proton::auto_list_enter ae2 (data_);
        while (pn_data_next(data_)) {
            provides.push_back (proton::get_string (data_));
        }
    }

    pn_data_next (data_);

    auto source = proton::readAndNext<std::string> (data_);
    auto descriptor = dispatchDescribed<schema::Descriptor> (data_);

    // SKIP the choices section **FOR NOW**

    return schema::Restricted::make (descriptor, name,
            label, provides, source);
}

/******************************************************************************
 *
 * Essentially, an enum.
 *
 ******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
ChoiceDescriptor::build (pn_data_t * data_) const {
    validateAndNext(data_);

    DBG ("CHOICE " << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
ReferencedObjectDescriptor::build (pn_data_t * data_) const {
    validateAndNext (data_);

    DBG ("REFERENCED OBJECT " << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
TransformSchemaDescriptor::build (pn_data_t * data_) const {
    validateAndNext (data_);

    DBG ("TRANSFORM SCHEMA " << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
TransformElementDescriptor::build (pn_data_t * data_) const {
    validateAndNext (data_);

    DBG ("TRANSFORM ELEMENT " << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
TransformElementKeyDescriptor::build (pn_data_t * data_) const {
    validateAndNext (data_);

    DBG ("TRANSFORM ELEMENT KEY" << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/
