# SIMM and Portfolio Demo - aka the Initial Margin Agreement Demo

## Background and SIMM Introduction

This app is a demonstration of how Corda can be used for the real world requirement of initial margin calculation and
agreement; featuring the integration of complex and industry proven third party libraries into Corda nodes.

SIMM is an acronym for "Standard Initial Margin Model". It is effectively the calculation of a "margin" that is paid
by one party to another when they agree a trade on certain types of transaction.

The SIMM was introduced to standardise the calculation of how much margin counterparties charge each other on their
bilateral transactions. Before SIMM, each counterparty computed margins according to its own model and it was made it very
 difficult to agree the exact margin with the counterparty that faces the same trade on the other side.

To enact this, in September 2016, the ISDA committee - with full backing from various governing bodies -
[issued a ruling on what is known as the ISDA SIMM ™ model](http://www2.isda.org/news/isda-simm-deployed-today-new-industry-standard-for-calculating-initial-margin-widely-adopted-by-market-participants)
a way of fairly and consistently calculating this margin. Any parties wishing to trade a financial product that is
covered under this ruling would, independently, use this model and calculate their margin payment requirement,
agree it with their trading counterparty and then pay (or receive, depending on the results of this calculation)
this amount. In the case of disagreement that is not resolved in a timely fashion, this payment would increase
and so therefore it is in the parties' interest to reach agreement in as short as time frame as possible.

To be more accurate, the SIMM calculation is not performed on just one trade - it is calculated on an aggregate of
intermediary values (which in this model are sensitivities to risk factors) from a portfolio of trades; therefore
the input to a SIMM is actually this data, not the individual trades themselves.

Also note that implementations of the SIMM are actually protected and subject to license restrictions by ISDA
(this is due to the model itself being protected). We were fortunate enough to technically partner with
[OpenGamma](http://www.opengamma.com)  who allowed us to demonstrate the SIMM process using their proprietary model.
In the source code released, we have replaced their analytics engine with very simple stub functions that allow
the process to run without actually calculating correct values, and can easily be swapped out in place for their real libraries.

## What happens in the demo (notionally)


Preliminaries
    - Ensure that there are a number of live trades with another party based on financial products that are covered under the
      ISDA SIMM agreement (if none, then use the demo to enter some simple trades as described below).

Initial Margin Agreement Process
    - Agree that one will be performing the margining calculation against a portfolio of trades with another party, and agree the trades in that portfolio. In practice, one node will start the flow but it does not matter which node does.
    - Individually (at the node level), identify the data (static, reference etc) one will need in order to be able to calculate the metrics on those trades
    - Confirm with the other counterparty the dataset from the above set
    - Calculate any intermediary steps and values needed for the margin calculation (ie sensitivities to risk factors)
    - Agree on the results of these steps
    - Calculate the initial margin
    - Agree on the calculation of the above with the other party
    - In practice, pay (or receive) this margin (omitted for the sake of complexity for this example)

## Requirements

This document assumes you have cURL (curl) installed and ready to use. It is usually installed by default in many Linux
distributions and MacOS.
On Windows, there are numerous ways of installation, including [Cygwin](https://www.cygwin.com), [official distribution](https://curl.haxx.se),
package managers like [Chocolatey](https://chocolatey.org), [NuGet](https://www.nuget.org/), or [Windows Linux subsystem](https://docs.microsoft.com/en-us/windows/wsl/about).
Please refer to installation documents of your chosen source.

## Demo execution (step by step)

**Setting up the Corda infrastructure**

To run from the command line in Unix:

1. Deploy the nodes using ``./gradlew samples:simm-valuation-demo:deployNodes``
2. Run the nodes using ``./samples/simm-valuation-demo/build/nodes/runnodes``

To run from the command line in Windows:

1. Deploy the nodes using ``gradlew samples:simm-valuation-demo:deployNodes``
2. Run the nodes using ``samples\simm-valuation-demo\build\nodes\runnodes``

**Getting Bank A's details**

From the command line run

    curl http://localhost:10005/api/simmvaluationdemo/whoami

The response should be something like

    {
        "self" : {
            "id" : "8Kqd4oWdx4KQGHGQW3FwXHQpjiv7cHaSsaAWMwRrK25bBJj792Z4rag7EtA",
            "text" : "C=GB,L=London,O=Bank A"
        },
        "counterparties" : [
            {
                "id" : "8Kqd4oWdx4KQGHGL1DzULumUmZyyokeSGJDY1n5M6neUfAj2sjbf65wYwQM",
                "text" : "C=JP,L=Tokyo,O=Bank C"
            },
            {
                "id" : "8Kqd4oWdx4KQGHGTBm34eCM2nrpcWKeM1ZG3DUYat3JTFUQTwB3Lv2WbPM8",
                "text" : "C=US,L=New York,O=Bank B"
            }
        ]
    }

Now, if we ask the same question of Bank C we will see that it's id matches the id for Bank C as a counter
party to Bank A and Bank A will appear as a counterparty

    curl -i -H "Content-Type: application/json" -X GET http://localhost:10011/api/simmvaluationdemo/whoami

**Creating a trade with Bank C**

In what follows, we assume we are Bank A (which is listening on port 10005)

Notice the id field in the output of the ``whoami`` command. We are going to use the id associated
with Bank C, one of our counterparties, to create a trade. The general command for this is:

    curl -i -H "Content-Type: application/json" -X PUT -d <<<JSON representation of the trade>>>  http://localhost:10005/api/simmvaluationdemo/<<<counterparty id>>>/trades

where the representation of the trade is


    {
      "id"          : "trade1",
      "description" : "desc",
      "tradeDate"   : [ 2016, 6, 6 ],
      "convention"  : "EUR_FIXED_1Y_EURIBOR_3M",
      "startDate"   : [ 2016, 6, 6 ],
      "endDate"     : [ 2020, 1, 2 ],
      "buySell"     : "BUY",
      "notional"    : "1000",
      "fixedRate"   : "0.1"
    }

Continuing our example, the specific command would look as follows

    curl -i -H "Content-Type: application/json" \
      -X PUT \
      -d '{"id":"trade1","description" : "desc","tradeDate" : [ 2016, 6, 6 ],  "convention" : "EUR_FIXED_1Y_EURIBOR_3M",  "startDate" : [ 2016, 6, 6 ],  "endDate" : [ 2020, 1, 2 ],  "buySell" : "BUY",  "notional" : "1000",  "fixedRate" : "0.1"}' \
      http://localhost:10005/api/simmvaluationdemo/8Kqd4oWdx4KQGHGL1DzULumUmZyyokeSGJDY1n5M6neUfAj2sjbf65wYwQM/trades

Note: you should replace the node id 8Kqd4oWdx4KQGHGL1DzULumUmZyyokeSGJDY1n5M6neUfAj2sjbf65wYwQM with the node id returned by the
whoami call above for one of the counterparties. In our worked example we selected "Bank C" and used the generated id for that node.
Thus, the actual command would be:

    curl -i -H "Content-Type: application/json" \
      -X PUT \
      -d '{"id":"trade1","description" : "desc","tradeDate" : [ 2016, 6, 6 ],  "convention" : "EUR_FIXED_1Y_EURIBOR_3M",  "startDate" : [ 2016, 6, 6 ],  "endDate" : [ 2020, 1, 2 ],  "buySell" : "BUY",  "notional" : "1000",  "fixedRate" : "0.1"}' \
      http://localhost:10005/api/simmvaluationdemo/<<<INSERT BANK C ID HERE>>/trades

Once executed, the expected response is:

    HTTP/1.1 202 Accepted
    Date: Thu, 28 Sep 2017 17:19:39 GMT
    Content-Type: text/plain
        Access-Control-Allow-Origin: *
    Content-Length: 2
    Server: Jetty(9.3.9.v20160517)

**Verifying trade completion**

With the trade completed and stored by both parties, the complete list of trades with our counterparty can be seen with the following command

    curl -X GET http://localhost:10005/api/simmvaluationdemo/<<<counterparty id>>>/trades

The command for our example, using Bank A, would thus be

    curl -X GET http://localhost:10005/api/simmvaluationdemo/8Kqd4oWdx4KQGHGL1DzULumUmZyyokeSGJDY1n5M6neUfAj2sjbf65wYwQM/trades

whilst a specific trade can be seen with


    curl  -X GET http://localhost:10005/api/simmvaluationdemo/<<<counterparty id>>>/trades/<<<trade id>>>

If we look at the trade we created above, we assigned it the id "trade1", the complete command in this case would be

    curl  -X GET http://localhost:10005/api/simmvaluationdemo/8Kqd4oWdx4KQGHGL1DzULumUmZyyokeSGJDY1n5M6neUfAj2sjbf65wYwQM/trades/trade1

**Generating a valuation**

    curl -i -H "Content-Type: application/json" \
      -X POST \
      -d <<<JSON representation>>>
      http://localhost:10005/api/simmvaluationdemo/<<<counterparty id>>>/portfolio/valuations/calculate

Again, the specific command to continue our example would be

    curl -i -H "Content-Type: application/json" \
      -X POST \
      -d '{"valuationDate":[2016,6,6]}' \
      http://localhost:10005/api/simmvaluationdemo/8Kqd4oWdx4KQGHGL1DzLumUmZyyokeSGJDY1n5M6neUfAj2sjbf65wYwQM/portfolio/valuations/calculate

**Viewing a valuation**

In the same way we can ask for specific instances of trades with a counterparty, we can request details of valuations

    curl -i -H "Content-Type: application/json" -X GET http://localhost:10005/api/simmvaluationdemo/<<<counterparty id>>>/portfolio/valuations

The specific command for out Bank A example is

    curl -i -H "Content-Type: application/json" \
      -X GET http://localhost:10005/api/simmvaluationdemo/8Kqd4oWdx4KQGHGL1DzULumUmZyyokeSGJDY1n5M6neUfAj2sjbf65YwQM/portfolio/valuations


## SIMM Library Licensing

This demo does not, however, include real SIMM valuation code but a stub for the OpenGamma set of libraries, so please do not base any financial decisions on results generated by this demo.

This demo was built in partnership with OpenGamma and used their SIMM library. However, due to licensing constraints we cannot distribute their library with this code. For this reason, we have stubbed out the relevant parts and replaced it with a very simplistic template that returns fake (but correctly structured) data. However, if you wish to use a realistic library, then please do get in touch with OpenGamma directly for access to their libraries and we will be happy to demonstrate how to replace the stub code.


## Troubleshooting

| Error | Fix |
|-------|------ |
| Could not find net.corda.(...):(...):0.6-SNAPSHOT | The corda libraries have not been installed into your local maven directory. View the instructions for doing this in the core corda repository |
| Execution failed for task ':simm-valuation-demo:buildWeb' : A problem occurred starting process 'command 'ng'' | You need to have `node packet manager` installed in order to build out some of the web resources. This is not a necessary step as we include pre-built web resources but if you do modify the web source, you will need to rebuild this area |

