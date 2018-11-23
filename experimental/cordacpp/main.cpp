#include "corda.h"
#include "all-messages.h"

#include <iostream>
#include <fstream>
#include <sstream>

using namespace std;
using namespace net::corda;

namespace transactions = net::corda::core::transactions;
namespace contracts = net::corda::core::contracts;

int main() {
    //ifstream stream("/Users/mike/Corda/open/stx1");
    ifstream stream("/tmp/wtx");
    string bits = string((istreambuf_iterator<char>(stream)), (istreambuf_iterator<char>()));

    if (bits.empty()) {
        cerr << "Failed to read file" << endl;
        return 1;
    }

    cout << dump(bits) << endl;
    auto wtx = parse<transactions::WireTransaction>(bits);
    cout << "This wtx has " << wtx->component_groups.size() << " component groups." << endl;
    cout << "The privacy salt is " << wtx->privacy_salt->bytes.size() << " bytes long." << endl;

    auto inputs = wtx->component_groups[ComponentGroupEnum::INPUTS]->components;
    auto outputs = wtx->component_groups[ComponentGroupEnum::OUTPUTS]->components;
    cout << "There are " << inputs.size() << " inputs and " << outputs.size() << " outputs." << endl;

    int out_index = 0;
    for (auto &out_slot : outputs) {
        auto output = parse<contracts::TransactionState<contracts::ContractState>>(out_slot->bytes);
        cout << "  Output " << out_index++ << " is governed by contract " << output->contract << endl;
    }

    return 0;
}
