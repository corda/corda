package net.corda.core.utilities
import com.github.kittinunf.fuel.httpPost

/**
 * Created by sangalli on 17/2/17.
 */

class OpReturnApi
{
    companion object
    {
        fun storeTxHashOnBlockchain(txHash: String)
        {
            println("Hash to be stored on blockchain: " + txHash)
            //view the transactions on:
            // https://testnet.smartbit.com.au/address/mnoQEPQe1D7hy2mvByJZk7cQ2JCd64cawA
            val url = "https://op-return.herokuapp.com/v2/saveTxHashInBlockchain/" + txHash
            val (request, response, result) = url.httpPost().responseString()
            println("Response from server:" + response)
        }
    }
}