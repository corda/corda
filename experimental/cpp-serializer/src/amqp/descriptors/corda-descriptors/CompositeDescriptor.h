#pragma once

#include "amqp/descriptors/AMQPDescriptor.h"

/******************************************************************************/

namespace amqp::internal {

    class CompositeDescriptor : public AMQPDescriptor {
        public :
            CompositeDescriptor() : AMQPDescriptor() { }
            CompositeDescriptor (const std::string &, int);

            ~CompositeDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;

            void read (
                pn_data_t *,
                std::stringstream &,
                const AutoIndent &) const override;
    };

}

/******************************************************************************/
