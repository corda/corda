////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_IDENTITY_CORDAX500NAME_H
#define NET_CORDA_CORE_IDENTITY_CORDAX500NAME_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace identity {

class CordaX500Name {
public:
    std::string common_name;
    std::string country;
    std::string locality;
    std::string organisation;
    std::string organisation_unit;
    std::string state;

    explicit CordaX500Name(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.identity.CordaX500Name", descriptor(), 6);
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, common_name); else decoder.next();
        net::corda::Parser::read_to(decoder, country);
        net::corda::Parser::read_to(decoder, locality);
        net::corda::Parser::read_to(decoder, organisation);
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, organisation_unit); else decoder.next();
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, state); else decoder.next();
    }

    const std::string descriptor() { return "net.corda:ngdwbt6kRT0l5nn16uf87A=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif