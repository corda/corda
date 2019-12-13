#include "Choice.h"

#include <iostream>

/******************************************************************************/

std::ostream &
amqp::internal::schema::
operator << (std::ostream & os_, const amqp::internal::schema::Choice & choice_) {
    os_ << choice_.m_choice;
    return os_;
}

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
