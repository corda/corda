#pragma once

/******************************************************************************/

#include "amqp/descriptors/AMQPDescriptor.h"

/******************************************************************************/

struct pn_data_t;

/******************************************************************************/

namespace amqp::internal {

    class ObjectDescriptor : public AMQPDescriptor {
    public :
        ObjectDescriptor() : AMQPDescriptor() { }

        ObjectDescriptor(const std::string & symbol_, int val_)
                : AMQPDescriptor(symbol_, val_)
        { }

        ~ObjectDescriptor() final = default;

        std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;

        void read (
                pn_data_t *,
                std::stringstream &,
                const AutoIndent &) const override;
    };

}

/******************************************************************************/
