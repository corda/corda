#pragma once

#include "amqp/schema/descriptors/AMQPDescriptor.h"

/******************************************************************************/

struct pn_data_t;

/******************************************************************************/

namespace amqp::internal::schema::descriptors {

    class SchemaDescriptor : public AMQPDescriptor {
    public :
        SchemaDescriptor() = delete;
        SchemaDescriptor (std::string, int);
        ~SchemaDescriptor() final = default;

        std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;

        void read (
                pn_data_t *,
                std::stringstream &,
                const AutoIndent &) const override;
    };

}

/******************************************************************************/
