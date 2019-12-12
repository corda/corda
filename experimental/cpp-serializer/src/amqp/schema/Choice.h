#pragma once

#include "AMQPTypeNotation.h"

/******************************************************************************/

namespace amqp::internal::schema {

    class Choice : public AMQPDescribed {
        private :
            std::string m_choice;
        public :
            Choice() = delete;

            explicit Choice (std::string);

            const std::string & choice() const;

    };

}

/******************************************************************************/
