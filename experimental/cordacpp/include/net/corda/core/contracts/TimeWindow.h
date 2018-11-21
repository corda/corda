////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_TIMEWINDOW_H
#define NET_CORDA_CORE_CONTRACTS_TIMEWINDOW_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace contracts {

class TimeWindow {
public:
    

    TimeWindow() = default;

    explicit TimeWindow(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.contracts.TimeWindow", descriptor(), 0);
        
    }

    virtual const std::string descriptor() { return "net.corda:vCzptpAY4Dzmk+ZJ0z0hgg=="; }
};

}
}
}
}

#endif