#pragma once

#include "amqp/descriptors/AMQPDescriptor.h"

/******************************************************************************/

struct pn_data_t;

/******************************************************************************/

namespace amqp::internal {

    class SchemaDescriptor : public AMQPDescriptor {
    public :
        SchemaDescriptor() = default;
        SchemaDescriptor (const std::string &, int);
        ~SchemaDescriptor() final = default;

        std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;

        void read (
                pn_data_t *,
                std::stringstream &,
                const AutoIndent &) const override;
    };

}

/******************************************************************************/
