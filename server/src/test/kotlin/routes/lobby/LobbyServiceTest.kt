package routes.lobby

import auth.Session
import auth.UserConnection
import http.dto.OnlineUser
import http.dto.RoomSummary
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import java.util.*
import org.junit.jupiter.api.Test
import room.Room
import server.EntropyServer
import store.MemoryUserConnectionStore
import store.RoomStore
import store.SessionStore
import store.Store
import store.UserConnectionStore
import testCore.AbstractTest
import testCore.verifyNotCalled
import util.XmlConstants
import util.makeGameSettings
import util.makeSession

class LobbyServiceTest : AbstractTest() {
    @Test
    fun `getLobby should return rooms and online users`() {
        val (service, _, sessionStore, _, roomStore) = makeService()

        val sessionA = makeSession(name = "Alyssa", achievementCount = 4)
        val sessionB = makeSession(name = "Bob", achievementCount = 7)
        sessionStore.putAll(sessionA, sessionB)

        val settings = makeGameSettings(includeMoons = true)
        val roomOne = Room(UUID.randomUUID(), "Carbon", settings, 4)
        val roomTwo = roomOne.makeCopy()

        roomStore.put(roomOne)
        roomStore.put(roomTwo)

        roomOne.attemptToJoinAsPlayer("Alyssa", 0)
        roomTwo.addToObservers("Bob")

        val lobby = service.getLobby()
        lobby.users.shouldContainExactlyInAnyOrder(OnlineUser("Alyssa", 4), OnlineUser("Bob", 7))
        lobby.rooms.shouldContainExactlyInAnyOrder(
            RoomSummary("Carbon 1", settings, 4, 1, 0),
            RoomSummary("Carbon 2", settings, 4, 0, 1),
        )
    }

    @Test
    fun `Should do nothing if there is no one online`() {
        val server = mockk<EntropyServer>(relaxed = true)
        val (service) = makeService(server = server)
        service.lobbyChanged()

        verifyNotCalled { server.sendViaNotificationSocket(any(), any(), any()) }
    }

    @Test
    fun `Should do nothing if told to exclude the only user`() {
        val server = mockk<EntropyServer>(relaxed = true)
        val (service, _, _, uscStore) = makeService(server = server)
        val usc = UserConnection("Alyssa")
        uscStore.put(usc)
        service.lobbyChanged(usc)

        verifyNotCalled { server.sendViaNotificationSocket(any(), any(), any()) }
    }

    @Test
    fun `Should notify the correct users`() {
        val server = mockk<EntropyServer>(relaxed = true)
        val (service, _, _, uscStore) = makeService(server = server)
        val uscA = UserConnection("Alyssa")
        val uscB = UserConnection("Bob")
        val uscC = UserConnection("Cat")
        uscStore.putAll(uscA, uscB, uscC)

        service.lobbyChanged(uscA)
        verify {
            server.sendViaNotificationSocket(
                listOf(uscB, uscC),
                any(),
                XmlConstants.SOCKET_NAME_LOBBY,
            )
        }

        clearMocks(server)
        service.lobbyChanged()
        verify {
            server.sendViaNotificationSocket(
                listOf(uscA, uscB, uscC),
                any(),
                XmlConstants.SOCKET_NAME_LOBBY,
            )
        }
    }

    private data class SetupParams(
        val service: LobbyService,
        val server: EntropyServer,
        val sessionStore: Store<UUID, Session>,
        val uscStore: UserConnectionStore,
        val roomStore: RoomStore,
    )

    private fun makeService(server: EntropyServer = EntropyServer()): SetupParams {
        val store = SessionStore()
        val uscStore = MemoryUserConnectionStore()
        val roomStore = RoomStore()
        return SetupParams(
            LobbyService(server, store, uscStore, roomStore),
            server,
            store,
            uscStore,
            roomStore,
        )
    }
}
