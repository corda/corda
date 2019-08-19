#pragma once

/******************************************************************************/

struct pn_data_t;

/******************************************************************************/

namespace amqp {
namespace internal {
namespace serialiser {

    class ProtonReader {
        public :
            template<typename T>
            virtual T read (pn_data_t *) {
            }
    }

}
}
}

/******************************************************************************/
