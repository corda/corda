#include "corda.h"
#include "all-messages.h"

#include <iostream>
#include <fstream>
#include <sstream>

using namespace std;

namespace corda = net::corda;
namespace transactions = net::corda::core::transactions;

int main() {
    //ifstream stream("/Users/mike/Corda/open/stx1");
    ifstream stream("/tmp/wtx");
    string bits = string((istreambuf_iterator<char>(stream)), (istreambuf_iterator<char>()));

    if (bits.empty()) {
        cerr << "Failed to read file" << endl;
        return 1;
    }

    cout << corda::dump(bits) << endl;
    auto wtx = corda::parse<net::corda::core::transactions::WireTransaction>(bits);
    return 0;
}
