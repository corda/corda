#pragma once

/******************************************************************************/

#include "amqp/schema/descriptors/AMQPDescriptor.h"

/******************************************************************************/

struct pn_data_t;

/******************************************************************************/

namespace amqp::internal::schema::descriptors {

    class ObjectDescriptor : public AMQPDescriptor {
    public :
        ObjectDescriptor() = delete;

        ObjectDescriptor (std::string, int);

        ~ObjectDescriptor() final = default;

        std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;

        void read (
                pn_data_t *,
                std::stringstream &,
                const AutoIndent &) const override;
    };

}

/******************************************************************************/
