#include "BlobInspector.h"
#include "CordaBytes.h"

#include <iostream>
#include <sstream>

#include "proton/codec.h"
#include "proton/proton_wrapper.h"

#include "amqp/schema/descriptors/AMQPDescriptorRegistory.h"

#include "amqp/CompositeFactory.h"
#include "amqp/schema/described-types/Envelope.h"

/******************************************************************************/

BlobInspector::BlobInspector (CordaBytes & cb_)
    : m_data { pn_data (cb_.size()) }
{
    // returns how many bytes we processed which right now we don't care
    // about but I assume there is a case where it doesn't process the
    // entire file
    auto rtn = pn_data_decode (m_data, cb_.bytes(), cb_.size());
    assert (rtn == cb_.size());
}

/******************************************************************************/

std::string
BlobInspector::dump() {
    std::unique_ptr<amqp::internal::schema::Envelope> envelope;

    if (pn_data_is_described (m_data)) {
        proton::auto_enter p (m_data);

        auto a = pn_data_get_ulong(m_data);

        envelope.reset (
                dynamic_cast<amqp::internal::schema::Envelope *> (
                        amqp::internal::AMQPDescriptorRegistory[a]->build(m_data).release()));
    }

    amqp::internal::CompositeFactory cf;

    cf.process (envelope->schema());

    auto reader = cf.byDescriptor (envelope->descriptor());
    assert (reader);

    {
        // move to the actual blob entry in the tree - ideally we'd have
        // saved this on the Envelope but that's not easily doable as we
        // can't grab an actual copy of our data pointer
        proton::auto_enter p (m_data);
        pn_data_next (m_data);
        proton::is_list (m_data);
        assert (pn_data_get_list (m_data) == 3);
        {
            proton::auto_enter p (m_data);

            std::stringstream ss;

            // We wrap our output like this to make sure it's valid JSON to
            // facilitate easy pretty printing
            ss << reader->dump ("{ Parsed", m_data, envelope->schema())->dump()
               << " }";

            return ss.str();
        }
    }
}

/******************************************************************************/
