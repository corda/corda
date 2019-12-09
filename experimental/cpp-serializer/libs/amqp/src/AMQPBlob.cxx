#include <sstream>

#include "AMQPBlob.h"

#include "amqp/src/schema/described-types/Envelope.h"
#include "amqp/src/schema/descriptors/AMQPDescriptorRegistory.h"
#include "amqp/include/assembler/ICompositeFactory.h"
#include "amqp/src/assembler/CompositeFactory.h"

#include "proton-wrapper/include/proton_wrapper.h"

amqp::
AMQPBlob::AMQPBlob (amqp::CordaBytes & cb_)
    : m_data { pn_data (cb_.size()) }
{
    // returns how many bytes we processed which right now we don't care
    // about but I assume there is a case where it doesn't process the
    // entire file

    auto rtn = pn_data_decode(m_data, cb_.bytes(), cb_.size());
    assert (rtn == cb_.size());
}

std::string
amqp::
AMQPBlob::dumpContents() const {
    std::unique_ptr<internal::schema::Envelope> envelope;

    if (pn_data_is_described (m_data)) {
        proton::auto_enter p (m_data);

        auto a = pn_data_get_ulong(m_data);

        envelope.reset (
                dynamic_cast<amqp::internal::schema::Envelope *> (
                        internal::AMQPDescriptorRegistory[a]->build(m_data).release()));
    }

    auto cf = internal::assembler::CompositeFactory();

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
