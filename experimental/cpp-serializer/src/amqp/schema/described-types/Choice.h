#pragma once

#include <iosfwd>

#include "schema/AMQPTypeNotation.h"

/******************************************************************************/

namespace amqp::internal::schema {

    class Choice : public AMQPDescribed {
        public :
            friend std::ostream & operator << (std::ostream &, const Choice &);

        private :
            std::string m_choice;

        public :
            Choice() = delete;

            explicit Choice (std::string);

            const std::string & choice() const;

    };

}

/******************************************************************************/
