#pragma once

#include "amqp/schema/descriptors/AMQPDescriptor.h"

/******************************************************************************/

namespace amqp::internal::schema::descriptors {

    class CompositeDescriptor : public AMQPDescriptor {
        public :
            CompositeDescriptor() = delete;
            CompositeDescriptor (std::string, int);

            ~CompositeDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;

            void read (
                pn_data_t *,
                std::stringstream &,
                const AutoIndent &) const override;
    };

}

/******************************************************************************/
