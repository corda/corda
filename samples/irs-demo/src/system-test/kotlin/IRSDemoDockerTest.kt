package net.corda.irs

import com.palantir.docker.compose.DockerComposeRule
import com.palantir.docker.compose.configuration.DockerComposeFiles
import com.palantir.docker.compose.connection.waiting.HealthChecks
import org.junit.ClassRule
import org.junit.Test
import org.openqa.selenium.By
import org.openqa.selenium.OutputType
import org.openqa.selenium.WebElement
import org.openqa.selenium.phantomjs.PhantomJSDriver
import org.openqa.selenium.support.ui.WebDriverWait
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IRSDemoDockerTest {
    companion object {

        private fun ensureSystemVariable(variable: String) {
            if (System.getProperty(variable) == null) {
                throw IllegalStateException("System variable $variable not set. Please refer to README file for proper setup instructions.")
            }
        }

        init {
            ensureSystemVariable("CORDAPP_DOCKER_COMPOSE")
            ensureSystemVariable("WEB_DOCKER_COMPOSE")
            ensureSystemVariable("phantomjs.binary.path")
        }

        @ClassRule
        @JvmField
        var docker = DockerComposeRule.builder()
                .files(DockerComposeFiles.from(
                        System.getProperty("CORDAPP_DOCKER_COMPOSE"),
                        System.getProperty("WEB_DOCKER_COMPOSE")))
                .waitingForService("web-a", HealthChecks.toRespondOverHttp(8080,  { port -> port.inFormat("http://\$HOST:\$EXTERNAL_PORT") }))
                .build()
    }

    @Test
    fun `runs IRS demo selenium phantomjs`() {


        val driver = PhantomJSDriver()

        val webAPort = docker.containers()
                .container("web-a")
                .port(8080)


        driver.get("http://${webAPort.ip}:${webAPort.externalPort}");

        //no deals on fresh interface
        val dealRows = driver.findElementsByCssSelector("table#deal-list tbody tr")
        assertTrue(dealRows.isEmpty())

        // Click Angular link and wait for form to appear
        val findElementByLinkText = driver.findElementByLinkText("Create Deal")
        findElementByLinkText.click()

        val driverWait = WebDriverWait(driver, 120)

        val form = driverWait.until<WebElement>({
            it?.findElement(By.cssSelector("form"))
        })

        form.submit()

        //Wait for deals to appear in a rows table
        val dealsList = driverWait.until<WebElement>({
            makeScreenshot(driver, "second")
            it?.findElement(By.cssSelector("table#deal-list tbody tr"))
        })

        assertNotNull(dealsList)
    }

    private fun makeScreenshot(driver: PhantomJSDriver, name: String) {
        val screenshotAs = driver.getScreenshotAs(OutputType.FILE)
        Files.copy(screenshotAs.toPath(), Paths.get("/Users", "maksymilianpawlak", "phantomjs", name + System.currentTimeMillis() + ".png"), StandardCopyOption.REPLACE_EXISTING)
    }
}
