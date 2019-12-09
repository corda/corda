#pragma once

/******************************************************************************/

#include <string>

#include "amqp/schema/descriptors/AMQPDescriptor.h"

/******************************************************************************/

namespace amqp::internal::schema::descriptors {

    class RestrictedDescriptor : public AMQPDescriptor {
        public :
            /**
             * This could just live as a stand alone function in the
             * anonymous namespace... but for testing purposes lets
             * just make it a static members of the class
             */
            static std::string makePrim (const std::string &);

    public :
        RestrictedDescriptor() = delete;
        RestrictedDescriptor (std::string, int);

        ~RestrictedDescriptor() final = default;

        std::unique_ptr<AMQPDescribed> build (pn_data_t *) const override;

        void read (
                pn_data_t *,
                std::stringstream &,
                const AutoIndent &) const override;
    };

}

/******************************************************************************/

