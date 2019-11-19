#include "Choice.h"

/******************************************************************************/

amqp::internal::schema::
Choice::Choice (std::string choice_)
     : m_choice (std::move (choice_))
{

}

/******************************************************************************/

const std::string &
amqp::internal::schema::
Choice::choice() const {
    return m_choice;
}

/******************************************************************************/
