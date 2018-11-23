////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_IDENTITY_PARTY_H
#define NET_CORDA_CORE_IDENTITY_PARTY_H

#include "corda.h"
#include "net/corda/core/identity/AbstractParty.h"

namespace net {
namespace corda {
namespace core {
namespace identity {
class CordaX500Name;
class AbstractParty;
class Party;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace identity {

class Party : public net::corda::core::identity::AbstractParty {
public:
    net::corda::ptr<net::corda::core::identity::CordaX500Name> name;

    Party() = default;

    explicit Party(proton::codec::decoder &decoder) : net::corda::core::identity::AbstractParty(decoder) {
        net::corda::Parser::read_to(decoder, name);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration14("net.corda:H9KOi8agUusgKKi3MEB3xg==", [](proton::codec::decoder &decoder) { return new net::corda::core::identity::Party(decoder); }); // NOLINT(cert-err58-cpp)

#endif