package routes.room

import auth.Session
import http.INVALID_ROOM_ID
import http.dto.JoinRoomRequest
import io.ktor.http.HttpStatusCode
import routes.ClientException
import store.RoomStore

class RoomService(private val roomStore: RoomStore) {
    fun joinRoom(session: Session, request: JoinRoomRequest) {
        val (roomId, playerNumber) = request
        val room =
            roomStore.find(roomId)
                ?: throw ClientException(
                    HttpStatusCode.Companion.BadRequest,
                    INVALID_ROOM_ID,
                    "No room exists for id $roomId",
                )

        val username = session.name
        if (playerNumber == null) {
            room.addToObservers(username)
        } else {
            val result = room.attemptToJoinAsPlayer(username, playerNumber)
        }
    }
}
