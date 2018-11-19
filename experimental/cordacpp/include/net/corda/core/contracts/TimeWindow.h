////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_TIMEWINDOW_H
#define NET_CORDA_CORE_CONTRACTS_TIMEWINDOW_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace contracts {

class TimeWindow {
public:
    

    explicit TimeWindow(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.contracts.TimeWindow", descriptor(), 0);
        
    }

    const std::string descriptor() { return "net.corda:vCzptpAY4Dzmk+ZJ0z0hgg=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif