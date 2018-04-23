#include "avian/arch.h"
#include <avian/system/system.h>
#include <avian/system/signal.h>

extern "C" {
    void debug_print(const char *msg);
}

namespace avian {
    namespace system {
        SignalRegistrar::SignalRegistrar()
        {
        }

        SignalRegistrar::~SignalRegistrar()
        {
        }

        bool SignalRegistrar::registerHandler(Signal signal UNUSED, Handler* handler UNUSED)
        {
            return true;
        }

        bool SignalRegistrar::unregisterHandler(Signal signal UNUSED)
        {
            return true;
        }

        void SignalRegistrar::setCrashDumpDirectory(const char*)
        {
        }
    }  // namespace system
}  // namespace avian

