package http.dto

import java.util.UUID

data class JoinRoomRequest(val roomId: UUID, val playerNumber: Int?)
