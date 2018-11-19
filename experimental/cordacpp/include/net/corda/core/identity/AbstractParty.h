////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_IDENTITY_ABSTRACTPARTY_H
#define NET_CORDA_CORE_IDENTITY_ABSTRACTPARTY_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace java {
namespace security {
class PublicKey;
}
}

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace identity {

class AbstractParty {
public:
    net::corda::ptr<java::security::PublicKey> owning_key;

    explicit AbstractParty(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.identity.AbstractParty", descriptor(), 1);
        net::corda::Parser::read_to(decoder, owning_key);
    }

    const std::string descriptor() { return "net.corda:jOvdaZkV+4P+ZufeAnkHJg=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif