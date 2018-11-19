////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_IDENTITY_PARTY_H
#define NET_CORDA_CORE_IDENTITY_PARTY_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace net {
namespace corda {
namespace core {
namespace identity {
class CordaX500Name;
class AbstractParty;
}
}
}
}
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

class Party : public net::corda::core::identity::AbstractParty {
public:
    net::corda::ptr<net::corda::core::identity::CordaX500Name> name;
    net::corda::ptr<java::security::PublicKey> owning_key;

    explicit Party(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.identity.Party", descriptor(), 2);
        net::corda::Parser::read_to(decoder, name);
        net::corda::Parser::read_to(decoder, owning_key);
    }

    const std::string descriptor() { return "net.corda:H9KOi8agUusgKKi3MEB3xg=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif