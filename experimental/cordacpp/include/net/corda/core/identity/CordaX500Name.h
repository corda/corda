////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_IDENTITY_CORDAX500NAME_H
#define NET_CORDA_CORE_IDENTITY_CORDAX500NAME_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace identity {

class CordaX500Name : public net::corda::Any {
public:
    std::string common_name;
    std::string country;
    std::string locality;
    std::string organisation;
    std::string organisation_unit;
    std::string state;

    CordaX500Name() = default;

    explicit CordaX500Name(proton::codec::decoder &decoder) {
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, common_name); else decoder.next();
        net::corda::Parser::read_to(decoder, country);
        net::corda::Parser::read_to(decoder, locality);
        net::corda::Parser::read_to(decoder, organisation);
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, organisation_unit); else decoder.next();
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, state); else decoder.next();
    }
};

}
}
}
}

net::corda::TypeRegistration Registration17("net.corda:ngdwbt6kRT0l5nn16uf87A==", [](proton::codec::decoder &decoder) { return new net::corda::core::identity::CordaX500Name(decoder); }); // NOLINT(cert-err58-cpp)

#endif