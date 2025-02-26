package plugins

import http.INTERNAL_SERVER_ERROR
import http.dto.ClientErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.logging.toLogString
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import routes.ClientException
import routes.dev.DevController
import routes.health.HealthCheckController
import routes.room.RoomController
import routes.session.SessionController
import utils.CoreGlobals.logger

fun Application.configureRouting() {
    install(StatusPages) {
        exception<ClientException> { call, cause ->
            logger.info(
                "clientError",
                "Error handling ${call.request.toLogString()}: ${cause.message}",
                "clientErrorCode" to cause.errorCode,
                "clientErrorMessage" to cause.message,
            )
            call.respond(cause.statusCode, ClientErrorResponse(cause.errorCode, cause.message))
        }

        exception<Throwable> { call, cause ->
            val errorMessage = "Error handling ${call.request.toLogString()}"
            logger.error("internalServerError", errorMessage, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ClientErrorResponse(INTERNAL_SERVER_ERROR, errorMessage),
            )
        }
    }

    HealthCheckController().installRoutes(this)
    SessionController().installRoutes(this)
    DevController().installRoutes(this)
    RoomController().installRoutes(this)
}
