import core.Contract
import core.crypto.SecureHash
import org.junit.Test
import java.net.URL
import java.net.URLClassLoader
import kotlin.test.assertEquals

class LoaderTests {

    @Test
    fun loadContracts() {
        var child = URLClassLoader(arrayOf(URL("file", "", "../contracts/build/libs/contracts.jar")))

        var contractClass = Class.forName("contracts.Cash", true, child)
        var contract = contractClass.newInstance() as Contract

        assertEquals(SecureHash.sha256("https://www.big-book-of-banking-law.gov/cash-claims.html"), contract.legalContractReference)
    }

}