#pragma once


#include <string>

#include "AMQPDescriptors.h"

/******************************************************************************
 *
 * Forward Class Declarations
 *
 ******************************************************************************/

struct pn_data_t;

/******************************************************************************
 *
 * class amqp::internal::EnvelopeDescriptor
 *
 ******************************************************************************/

namespace amqp::internal::schema::descriptors {

    class EnvelopeDescriptor : public AMQPDescriptor {
        public :
            EnvelopeDescriptor() = delete;
            EnvelopeDescriptor (std::string, int);

            ~EnvelopeDescriptor() final = default;

            std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;

            void read (
                    pn_data_t *,
                    std::stringstream &,
                    const AutoIndent &) const override;
    };

}

/******************************************************************************/
