////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_CONTRACTSTATE_H
#define NET_CORDA_CORE_CONTRACTS_CONTRACTSTATE_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace contracts {

class ContractState : public net::corda::Any {
public:
    

    ContractState() = default;

    explicit ContractState(proton::codec::decoder &decoder) {
        
    }
};

}
}
}
}

net::corda::TypeRegistration Registration4("net.corda:Z2i2Bmo52EfucFXZ8B2CPQ==", [](proton::codec::decoder &decoder) { return new net::corda::core::contracts::ContractState(decoder); }); // NOLINT(cert-err58-cpp)

#endif