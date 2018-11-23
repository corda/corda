////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_TIMEWINDOW_H
#define NET_CORDA_CORE_CONTRACTS_TIMEWINDOW_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace contracts {

class TimeWindow : public net::corda::Any {
public:
    

    TimeWindow() = default;

    explicit TimeWindow(proton::codec::decoder &decoder) {
        
    }
};

}
}
}
}

net::corda::TypeRegistration Registration17("net.corda:vCzptpAY4Dzmk+ZJ0z0hgg==", [](proton::codec::decoder &decoder) { return new net::corda::core::contracts::TimeWindow(decoder); }); // NOLINT(cert-err58-cpp)

#endif