#pragma once

/******************************************************************************/

#include <map>
#include <string>
#include <iostream>

#include "amqp/AMQPDescribed.h"

/******************************************************************************
 *
 * Forward type declarations
 *
 ******************************************************************************/

struct pn_data_t;

/******************************************************************************
 *
 * amqp::internal::AMQPDescriptor
 *
 ******************************************************************************/

namespace amqp::internal {

    class AMQPDescriptor {
        protected :
            std::string m_symbol;
            int32_t m_val;

        public :
            AMQPDescriptor()
                : m_symbol ("ERROR")
                , m_val (-1)
            { }

            AMQPDescriptor(std::string symbol_, int val_)
                : m_symbol (std::move (symbol_))
                , m_val (val_)
            { }

            virtual ~AMQPDescriptor() = default;

            const std::string & symbol() const { return m_symbol; }

            void validateAndNext (pn_data_t *) const;

            virtual std::unique_ptr<AMQPDescribed> build (pn_data_t * data_) const = 0;
    };

}

/******************************************************************************/
