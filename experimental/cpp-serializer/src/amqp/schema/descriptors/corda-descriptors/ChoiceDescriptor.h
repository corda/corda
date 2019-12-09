#pragma once

#include "amqp/schema/descriptors/AMQPDescriptor.h"

/******************************************************************************
 *
 * Represents an enumeration
 *
 ******************************************************************************/

namespace amqp::internal::schema::descriptors {

    class ChoiceDescriptor : public AMQPDescriptor {
        public :
            ChoiceDescriptor() = delete;

            ChoiceDescriptor (std::string, int);

            ~ChoiceDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;
    };

}

/******************************************************************************/
