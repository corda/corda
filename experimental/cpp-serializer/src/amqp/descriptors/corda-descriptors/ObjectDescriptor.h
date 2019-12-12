#pragma once

/******************************************************************************/

#include "amqp/descriptors/AMQPDescriptor.h"

/******************************************************************************/

struct pn_data_t;

/******************************************************************************/

namespace amqp::internal {

    class ObjectDescriptor : public AMQPDescriptor {
    public :
        ObjectDescriptor() = delete;

        ObjectDescriptor(const std::string &, int);

        ~ObjectDescriptor() final = default;

        std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;

        void read (
                pn_data_t *,
                std::stringstream &,
                const AutoIndent &) const override;
    };

}

/******************************************************************************/
