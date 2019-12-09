#include "ObjectDescriptor.h"

#include "types.h"
#include "debug.h"

#include "proton/proton_wrapper.h"
#include "amqp/schema/described-types/Descriptor.h"

#include <sstream>

/******************************************************************************
 *
 * amqp::internal::schema::descriptors::ObjectDescriptor
 *
 ******************************************************************************/

amqp::internal::schema::descriptors::
ObjectDescriptor::ObjectDescriptor (
    std::string symbol_,
    int val_
) : AMQPDescriptor (std::move (symbol_), val_) {
}

/******************************************************************************/

/**
 *
 */
uPtr<amqp::AMQPDescribed>
amqp::internal::schema::descriptors::
ObjectDescriptor::build (pn_data_t * data_) const {
    DBG ("DESCRIPTOR" << std::endl); // NOLINT

    validateAndNext (data_);

    proton::auto_enter p (data_);

    auto symbol = proton::get_symbol<std::string> (data_);

    return std::make_unique<schema::Descriptor> (symbol);
}

/******************************************************************************/

void
amqp::internal::schema::descriptors::
ObjectDescriptor::read (
        pn_data_t * data_,
        std::stringstream & ss_,
        const AutoIndent & ai_
) const  {
    proton::is_list (data_);

    {
        AutoIndent ai { ai_ };
        proton::auto_list_enter ale (data_);
        pn_data_next(data_);

        ss_ << ai << "1/2] "
            << proton::get_symbol<std::string>(
                          (pn_data_t *)proton::auto_next (data_))
            << std::endl;

        ss_ << ai << "2/2] " << data_ << std::endl;
    }
}

/******************************************************************************/
