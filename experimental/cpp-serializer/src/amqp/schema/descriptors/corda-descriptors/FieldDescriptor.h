#pragma once

/******************************************************************************/

#include "proton/codec.h"
#include "amqp/AMQPDescribed.h"
#include "amqp/schema/descriptors/AMQPDescriptor.h"

/******************************************************************************/

namespace amqp::internal::schema::descriptors {

    class FieldDescriptor : public AMQPDescriptor {
        public :
            FieldDescriptor() = delete;
            FieldDescriptor (std::string, int);

            ~FieldDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;

            void read (
                pn_data_t *,
                std::stringstream &,
                const AutoIndent &) const override;
    };

}

/******************************************************************************/
