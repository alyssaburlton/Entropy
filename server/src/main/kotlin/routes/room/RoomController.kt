package routes.room

import http.Routes
import http.dto.JoinRoomRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import routes.requiresSession
import util.ServerGlobals.roomService

class RoomController {
    fun installRoutes(application: Application) {
        application.routing { get(Routes.JOIN_ROOM) { doJoinRoom(call) } }
    }

    private suspend fun doJoinRoom(call: ApplicationCall) =
        requiresSession(call) { session ->
            val request = call.receive<JoinRoomRequest>()
            roomService.joinRoom(session, request)
            call.respond(HttpStatusCode.NoContent)
        }
}
