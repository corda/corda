#pragma once

/******************************************************************************/

#include <string>

#include "amqp/descriptors/AMQPDescriptor.h"

/******************************************************************************/

namespace amqp::internal {

    class RestrictedDescriptor : public AMQPDescriptor {
    public :
        RestrictedDescriptor() : AMQPDescriptor() { }

        RestrictedDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
        { }

        ~RestrictedDescriptor() final = default;

        std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;

        void read (
                pn_data_t *,
                std::stringstream &,
                const AutoIndent &) const override;
    };

}

/******************************************************************************/

