#pragma once

#include "amqp/descriptors/AMQPDescriptor.h"

/******************************************************************************
 *
 * Represents an enumeration
 *
 ******************************************************************************/

namespace amqp::internal {

    class ChoiceDescriptor : public AMQPDescriptor {
        public :
            ChoiceDescriptor() = delete;

            ChoiceDescriptor (const std::string &, int);

            ~ChoiceDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/
