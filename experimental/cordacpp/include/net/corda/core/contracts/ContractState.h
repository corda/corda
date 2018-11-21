////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_CONTRACTSTATE_H
#define NET_CORDA_CORE_CONTRACTS_CONTRACTSTATE_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace contracts {

class ContractState {
public:
    

    ContractState() = default;

    explicit ContractState(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "interface net.corda.core.contracts.ContractState", descriptor(), 0);
        
    }

    virtual const std::string descriptor() { return "net.corda:Z2i2Bmo52EfucFXZ8B2CPQ=="; }
};

}
}
}
}

#endif