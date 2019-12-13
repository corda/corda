#include "CompositeDescriptor.h"

#include <string>
#include <sstream>
#include <iostream>

#include "types.h"
#include "debug.h"

#include "proton/proton_wrapper.h"

#include "amqp/schema/descriptors/AMQPDescriptors.h"

#include "amqp/schema/field-types/Field.h"
#include "amqp/schema/described-types/Composite.h"
#include "amqp/schema/described-types/Descriptor.h"

/******************************************************************************
 *
 * amqp::internal::schema::descriptors::CompositeDescriptor
 *
 ******************************************************************************/

amqp::internal::schema::descriptors::
CompositeDescriptor::CompositeDescriptor (
    std::string symbol_,
    int val_
) : AMQPDescriptor (std::move (symbol_), val_) {

}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::schema::descriptors::
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

    pn_data_next (data_);

    /* descriptor: Descriptor */
    auto descriptor = descriptors::dispatchDescribed<schema::Descriptor>(data_);

    pn_data_next (data_);

    /* fields: List<Described>*/
    std::vector<uPtr<schema::Field>> fields;
    fields.reserve (pn_data_get_list (data_));
    {
        proton::auto_list_enter p2 (data_);
        while (pn_data_next (data_)) {
            fields.emplace_back (descriptors::dispatchDescribed<schema::Field>(data_));
        }
    }

    return std::make_unique<schema::Composite> (
            schema::Composite (
                    std::move (name),
                    std::move (label),
                    std::move (provides),
                    std::move (descriptor),
                    std::move (fields)));
}

/******************************************************************************/

void
amqp::internal::schema::descriptors::
CompositeDescriptor::read (
        pn_data_t * data_,
        std::stringstream & ss_,
        const AutoIndent & ai_
) const {
    proton::is_list(data_);

    {
        AutoIndent ai { ai_ };
        proton::auto_enter p (data_);

        proton::is_string (data_);
        ss_ << ai
            << "1] String: ClassName: "
            << proton::readAndNext<std::string>(data_)
            << std::endl;

        proton::is_string (data_);
        ss_ << ai
            << "2] String: Label: \""
            << proton::readAndNext<std::string>(data_, true)
            << "\"" << std::endl;

        proton::is_list (data_);

        ss_ << ai << "3] List: Provides: [ ";
        {
            proton::auto_list_enter ale (data_);
            while (pn_data_next(data_)) {
                ss_ << ai << (proton::get_string (data_)) << " ";
            }
        }
        ss_ << "]" << std::endl;

        pn_data_next (data_);
        proton::is_described (data_);

        ss_ << ai << "4] Descriptor:" << std::endl;

        AMQPDescriptorRegistory[pn_data_type(data_)]->read (
            (pn_data_t *)proton::auto_next(data_), ss_, AutoIndent { ai });

        ss_ << ai << "5] List: Fields: " << std::endl;
        {
            AutoIndent ai2 { ai };

            proton::auto_list_enter ale (data_);
            for (int i { 1 } ; pn_data_next (data_) ; ++i) {
                ss_ << ai2 << i << "/"
                    << ale.elements() << "]"
                    << std::endl;

                AMQPDescriptorRegistory[pn_data_type(data_)]->read (
                        data_, ss_, AutoIndent { ai2 });
            }
        }
    }
}

/******************************************************************************/
