#include "Descriptor.h"

/******************************************************************************/

namespace amqp::internal::schema {

    std::ostream &
    operator << (std::ostream & stream_, const Descriptor & desc_) {
        stream_ << desc_.m_name;
        return stream_;
    }

}

/******************************************************************************
 *
 * Descriptor Implementation
 *
 ******************************************************************************/

amqp::internal::schema::
Descriptor::Descriptor (std::string name_)
    : m_name (std::move (name_))
{ }

/******************************************************************************/

const std::string &
amqp::internal::schema::
Descriptor::name() const {
    return m_name;
}

/******************************************************************************/
