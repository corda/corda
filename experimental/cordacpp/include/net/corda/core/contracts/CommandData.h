////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_COMMANDDATA_H
#define NET_CORDA_CORE_CONTRACTS_COMMANDDATA_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace contracts {

class CommandData : public net::corda::Any {
public:
    

    CommandData() = default;

    explicit CommandData(proton::codec::decoder &decoder) {
        
    }
};

}
}
}
}

net::corda::TypeRegistration Registration24("net.corda:fq6RP/f3tjDhoj0Yx6ipAg==", [](proton::codec::decoder &decoder) { return new net::corda::core::contracts::CommandData(decoder); }); // NOLINT(cert-err58-cpp)

#endif