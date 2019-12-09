#include <iostream>
#include <iomanip>
#include <fstream>
#include <cstddef>

#include <assert.h>
#include <string.h>
#include <proton/types.h>
#include <proton/codec.h>
#include <sys/stat.h>

#include "amqp/include/CordaBytes.h"

/******************************************************************************/

void
printNode (pn_data_t * d_) {
    /*
    std::stringstream ss;

    if (pn_data_is_described (d_)) {
        amqp::internal::AMQPDescriptorRegistory[22UL]->read (d_, ss);
    }

    std::cout << ss.str() << std::endl;
     */
}


/******************************************************************************/

int
main (int argc, char **argv) {

    struct stat results { };

    if (stat(argv[1], &results) != 0) {
        return EXIT_FAILURE;
    }

    amqp::CordaBytes cb (argv[1]);

    if (cb.encoding() == amqp::DATA_AND_STOP) {
//        BlobInspector blobInspector (cb);
//        auto val = blobInspector.dump();
//        std::cout << val << std::endl;
    } else {
        std::cerr << "BAD ENCODING " << cb.encoding() << " != "
                  << amqp::DATA_AND_STOP << std::endl;

        return EXIT_FAILURE;
    }

    return EXIT_SUCCESS;
}
