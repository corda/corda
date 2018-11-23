////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_CORETRANSACTION_H
#define NET_CORDA_CORE_TRANSACTIONS_CORETRANSACTION_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace transactions {

class CoreTransaction : public net::corda::Any {
public:
    

    CoreTransaction() = default;

    explicit CoreTransaction(proton::codec::decoder &decoder) {
        
    }
};

}
}
}
}

net::corda::TypeRegistration Registration18("net.corda:jEgCUtI5OFh9Bzx98/Absw==", [](proton::codec::decoder &decoder) { return new net::corda::core::transactions::CoreTransaction(decoder); }); // NOLINT(cert-err58-cpp)

#endif