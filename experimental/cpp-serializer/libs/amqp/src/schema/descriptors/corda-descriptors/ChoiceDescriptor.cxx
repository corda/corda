#include "described-types/Choice.h"
#include "ChoiceDescriptor.h"

#include "corda-utils/include/types.h"

#include "proton-wrapper/include/proton_wrapper.h"

/******************************************************************************/

amqp::internal::schema::descriptors::
ChoiceDescriptor::ChoiceDescriptor (
    std::string symbol_,
    int val_
) : AMQPDescriptor (std::move (symbol_), val_) {
}

/******************************************************************************/

std::unique_ptr<amqp::AMQPDescribed>
amqp::internal::schema::descriptors::
ChoiceDescriptor::build (pn_data_t * data_) const  {
    validateAndNext (data_);
    proton::auto_enter ae (data_);

    auto name = proton::get_string (data_);

    return std::make_unique<schema::Choice> (name);
}

/******************************************************************************/
