#include "AMQPDescriptor.h"

#include <sstream>
#include <amqp/schema/descriptors/corda-descriptors/EnvelopeDescriptor.h>

#include "proton/proton_wrapper.h"
#include "AMQPDescriptorRegistory.h"

/******************************************************************************/

namespace amqp::internal::schema::descriptors {

    std::ostream &
    operator<<(std::ostream &stream_, const AutoIndent &ai_) {
        stream_ << ai_.indent;
        return stream_;
    }

}

/******************************************************************************/

const std::string &
amqp::internal::schema::descriptors::
AMQPDescriptor::symbol() const {
    return m_symbol;
}

/******************************************************************************/

std::unique_ptr<amqp::AMQPDescribed>
amqp::internal::schema::descriptors::
AMQPDescriptor::build (pn_data_t *) const {
    throw std::runtime_error ("Should never be called");
}

/******************************************************************************/

inline void
amqp::internal::schema::descriptors::
AMQPDescriptor::read (
        pn_data_t * data_,
        std::stringstream & ss_
) const {
    return read (data_, ss_, AutoIndent());
}

/******************************************************************************/

void
amqp::internal::schema::descriptors::
AMQPDescriptor::read (
        pn_data_t * data_,
        std::stringstream & ss_,
        const AutoIndent & ai_
) const {
    switch (pn_data_type (data_)) {
        case PN_DESCRIBED : {
            ss_ << ai_ << "DESCRIBED: " << std::endl;
            {
                AutoIndent ai { ai_ } ; // NOLINT
                proton::auto_enter p (data_);

                switch (pn_data_type (data_)) {
                    case PN_ULONG : {
                        auto key = proton::readAndNext<u_long>(data_);

                        ss_ << ai << "key  : "
                            << key << " :: " << amqp::stripCorda(key)
                            << " -> "
                            <<  amqp::describedToString ((uint64_t )key)
                            << std::endl;

                        proton::is_list (data_);
                        ss_ << ai << "list : entries: "
                            << pn_data_get_list(data_)
                            << std::endl;

                        AMQPDescriptorRegistory[key]->read (data_, ss_, ai);
                        break;
                    }
                    case PN_SYMBOL : {
                        ss_ << ai << "blob: bytes: "
                            << pn_data_get_symbol(data_).size
                            << std::endl;
                        break;
                    }
                    default : {
                        throw std::runtime_error (
                            "Described type should only contain long or blob");
                    }
                }
            }
            break;
        }
        default : {
            throw std::runtime_error ("Can only dispatch described objects");
        }
    }
}

/******************************************************************************/
