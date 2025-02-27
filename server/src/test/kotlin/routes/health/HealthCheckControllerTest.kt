package routes.health

import http.Routes
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import testCore.shouldMatchJson
import util.ApplicationTest
import util.OnlineConstants

class HealthCheckControllerTest : ApplicationTest() {
    @Test
    fun `Should respond to a health check request`() = testApplication {
        val response = client.get(Routes.HEALTH_CHECK)
        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldMatchJson
            """
            {
                "apiVersion": ${OnlineConstants.API_VERSION}
            }
        """
                .trimIndent()
    }
}
