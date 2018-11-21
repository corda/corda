////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_CORETRANSACTION_H
#define NET_CORDA_CORE_TRANSACTIONS_CORETRANSACTION_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace transactions {

class CoreTransaction {
public:
    

    CoreTransaction() = default;

    explicit CoreTransaction(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.transactions.CoreTransaction", descriptor(), 0);
        
    }

    virtual const std::string descriptor() { return "net.corda:jEgCUtI5OFh9Bzx98/Absw=="; }
};

}
}
}
}

#endif