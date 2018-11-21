////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_COMMANDDATA_H
#define NET_CORDA_CORE_CONTRACTS_COMMANDDATA_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace contracts {

class CommandData {
public:
    

    CommandData() = default;

    explicit CommandData(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "interface net.corda.core.contracts.CommandData", descriptor(), 0);
        
    }

    virtual const std::string descriptor() { return "net.corda:fq6RP/f3tjDhoj0Yx6ipAg=="; }
};

}
}
}
}

#endif