package room

import auth.UserConnection
import game.GameSettings
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import `object`.Bid
import `object`.BidHistory
import `object`.ExtendedConcurrentHashMap
import `object`.GameWrapper
import `object`.HandDetails
import `object`.LeftBid
import `object`.OnlineMessage
import `object`.Player
import store.IHasId
import util.CardsUtil
import util.EntropyUtil
import util.ServerGlobals
import util.ServerGlobals.roomStore
import util.ServerGlobals.uscStore
import util.XmlBuilderServer
import util.XmlConstants
import utils.CoreGlobals.logger

data class Room(
    override val id: UUID,
    private val baseName: String,
    val settings: GameSettings,
    val capacity: Int,
    private val index: Int = 1,
) : IHasId<UUID> {
    private val hmPlayerByPlayerNumber = ExtendedConcurrentHashMap<Int, String>()
    private val hmFormerPlayerByPlayerNumber: ConcurrentHashMap<Int, String> = ConcurrentHashMap()
    val chatHistory: MutableList<OnlineMessage> = mutableListOf()
    private val currentPlayers: MutableList<String> = mutableListOf()
    private val observers: MutableList<String> = mutableListOf()
    private var previousGame: GameWrapper? = null
    private var currentGame: GameWrapper = initialiseGame()

    val name: String
        get() = "$baseName $index"

    val isCopy: Boolean
        get() = index > 1

    val isFull: Boolean
        get() = currentPlayers.size == capacity

    private val isEmpty: Boolean
        get() = (currentPlayers.isEmpty() && observers.isEmpty())

    private fun clearChatIfEmpty() {
        synchronized(this) {
            if (!isEmpty) {
                return
            }
            chatHistory.clear()
        }
    }

    fun attemptToJoinAsPlayer(username: String, playerNumber: Int): Int? {
        synchronized(this) {
            val existingUsername: String? = hmPlayerByPlayerNumber[playerNumber]
            if (existingUsername != null) {
                logger.info(
                    "seatTaken",
                    "$username tried to join $name as player $playerNumber but seat was taken by $existingUsername",
                )
                return null
            }

            if (hmPlayerByPlayerNumber.containsValue(username)) {
                logger.info("doubleJoin", "$username tried to join $name twice!")
                return null
            }

            currentPlayers.add(username)
            hmPlayerByPlayerNumber[playerNumber] = username
            observers.remove(username)

            if (isFull) {
                roomStore.addCopy(this)
            }

            notifyAllPlayersOfPlayerChange(username, false)
            ServerGlobals.lobbyService.lobbyChanged()
            return playerNumber
        }
    }

    fun removePlayer(username: String, fireLobbyChanged: Boolean) {
        for (playerNumber in 0..<capacity) {
            val user: String? = hmPlayerByPlayerNumber[playerNumber]
            if ((user != null && (username == user))) {
                hmPlayerByPlayerNumber.remove(playerNumber)
                hmFormerPlayerByPlayerNumber[playerNumber] = user

                // Notify everyone in the room that this player has left. Block on this.
                notifyAllPlayersOfPlayerChange(username, true)

                // The game has not started
                if (currentGame.gameStartMillis == -1L) {
                    // Unset the countdown if it's going, reset current capacity and get out of this
                    // madness
                    currentGame.countdownStartMillis = -1
                    resetCurrentPlayers(fireLobbyChanged)
                    return
                }

                // There is a game in progress
                if (currentGame.gameEndMillis == -1L) {
                    val bid = LeftBid()
                    val player =
                        Player(playerNumber, EntropyUtil.getColourForPlayerNumber(playerNumber))
                    player.name = username
                    bid.player = player

                    val history: BidHistory = currentGame.currentBidHistory
                    history.addBidForPlayer(playerNumber, bid)

                    // Moved this into here as otherwise we set it to 0 incorrectly and a person
                    // ends up with no cards!
                    val details: HandDetails = currentGame.currentRoundDetails
                    val hmHandSizeByPlayerNumber = details.handSizes
                    hmHandSizeByPlayerNumber[playerNumber] = 0
                }

                val playerSize: Int = hmPlayerByPlayerNumber.size
                if (playerSize == 1) {
                    val remainingPlayerNumber: Int = hmPlayerByPlayerNumber.onlyKey
                    finishCurrentGame(remainingPlayerNumber)
                } else if (playerSize == 0) {
                    resetCurrentPlayers(fireLobbyChanged)
                    clearChatIfEmpty()
                }
            }
        }
    }

    private fun notifyAllPlayersOfPlayerChange(userToExclude: String, blocking: Boolean) {
        val notification: String = XmlBuilderServer.getPlayerNotification(this)
        notifyAllUsersViaGameSocket(notification, userToExclude, blocking)
    }

    private fun notifyAllUsersViaGameSocket(
        notification: String,
        userToExclude: String?,
        blocking: Boolean,
    ) {
        var usersToNotify = allUsersInRoom
        if (userToExclude != null) {
            usersToNotify = usersToNotify.minus(userToExclude)
        }

        val uscs: List<UserConnection> = uscStore.getAllForNames(usersToNotify)
        ServerGlobals.server.sendViaNotificationSocket(
            uscs,
            notification,
            XmlConstants.SOCKET_NAME_GAME,
            blocking,
        )
    }

    private fun finishCurrentGame(winningPlayer: Int) {
        val roundNumber: Int = currentGame.roundNumber
        val winningUsername: String? = hmPlayerByPlayerNumber[winningPlayer]
        resetCurrentPlayers()
        currentGame.winningPlayer = winningPlayer

        if (roundNumber > 1) {
            val om = OnlineMessage("black", "$winningUsername won!", "Game")
            addToChatHistoryAndNotifyUsers(om)
        }

        previousGame = currentGame.factoryCopy()
        currentGame = initialiseGame()

        // Notify everyone that the game is over now we've finished setting up
        val notification: String = XmlBuilderServer.factoryGameOverNotification(this, winningPlayer)
        notifyAllUsersViaGameSocket(notification, null, false)
    }

    @JvmOverloads
    fun resetCurrentPlayers(fireLobbyChanged: Boolean = true) {
        currentPlayers.clear()
        for (i in 0..<capacity) {
            val username: String? = hmPlayerByPlayerNumber[i]
            if (username != null) {
                currentPlayers.add(username)
            }
        }

        hmFormerPlayerByPlayerNumber.clear()
        if (fireLobbyChanged) {
            ServerGlobals.lobbyService.lobbyChanged()
        }
    }

    fun addToObservers(username: String) {
        synchronized(this) {
            if (!observers.contains(username)) {
                observers.add(username)
            }
            removePlayer(username, false)
            ServerGlobals.lobbyService.lobbyChanged()
        }
    }

    fun removeFromObservers(username: String) {
        val removed: Boolean = observers.remove(username)
        if (removed) {
            ServerGlobals.lobbyService.lobbyChanged()
        }

        clearChatIfEmpty()
    }

    private fun initialiseGame(): GameWrapper {
        val gameId: String = "G" + System.currentTimeMillis()

        val newGame = GameWrapper(gameId)

        val details = HandDetails()
        val hmHandSizeByPlayerNumber = ExtendedConcurrentHashMap<Int, Int>()
        for (i in 0..<capacity) {
            hmHandSizeByPlayerNumber[i] = 5
        }

        val hmHandByPlayerNumber = dealHandsHashMap(hmHandSizeByPlayerNumber)
        details.hands = hmHandByPlayerNumber
        details.handSizes = hmHandSizeByPlayerNumber
        newGame.setDetailsForRound(1, details)

        val personToStart = Random().nextInt(capacity)

        val history = BidHistory()
        history.personToStart = personToStart
        newGame.setBidHistoryForRound(1, history)

        return newGame
    }

    fun handleChallenge(
        gameId: String,
        roundNumber: Int,
        playerNumber: Int,
        challengedNumber: Int,
        bid: Bid,
    ) {
        val game = getGameForId(gameId)
        val details: HandDetails = game.getDetailsForRound(roundNumber)
        val hmHandByPlayerNumber: ConcurrentHashMap<Int, List<String>> = details.hands
        if (bid.isOverbid(hmHandByPlayerNumber, settings.jokerValue)) {
            // bidder loses
            setUpNextRound(challengedNumber)
        } else {
            // challenger loses
            setUpNextRound(playerNumber)
        }
    }

    fun handleIllegal(
        gameId: String,
        roundNumber: Int,
        playerNumber: Int,
        bidderNumber: Int,
        bid: Bid,
    ) {
        val game = getGameForId(gameId)
        val details: HandDetails = game.getDetailsForRound(roundNumber)
        val hmHandByPlayerNumber: ConcurrentHashMap<Int, List<String>> = details.hands
        if (
            bid.isPerfect(
                hmHandByPlayerNumber,
                settings.jokerValue,
                settings.includeMoons,
                settings.includeStars,
            )
        ) {
            setUpNextRound(bidderNumber)
        } else {
            setUpNextRound(playerNumber)
        }
    }

    private fun setUpNextRound(losingPlayerNumber: Int) {
        val currentRoundNumber: Int = currentGame.roundNumber

        var nextRoundDetails: HandDetails? = currentGame.getDetailsForRound(currentRoundNumber + 1)
        if (nextRoundDetails != null) {
            logger.error("doubleRound", "Trying to set up next round but it's not null. Room $name")
            return
        }

        val currentRoundDetails: HandDetails = currentGame.getDetailsForRound(currentRoundNumber)
        val hmHandSizeByPlayerNumber = currentRoundDetails.handSizes
        val handSize = hmHandSizeByPlayerNumber.getValue(losingPlayerNumber)
        val newHandSize: Int = max(0, handSize - 1)

        val hmHandSizeByPlayerNumberForNextRound = hmHandSizeByPlayerNumber.factoryCopy()
        hmHandSizeByPlayerNumberForNextRound[losingPlayerNumber] = newHandSize

        val potentialWinner: Int = getWinningPlayer(hmHandSizeByPlayerNumberForNextRound)
        if (potentialWinner > -1) {
            finishCurrentGame(potentialWinner)
        } else {
            nextRoundDetails = HandDetails()
            nextRoundDetails.handSizes = hmHandSizeByPlayerNumberForNextRound

            val hmHandByPlayerNumber: ConcurrentHashMap<Int, List<String>> =
                dealHandsHashMap(hmHandSizeByPlayerNumberForNextRound)

            nextRoundDetails.hands = hmHandByPlayerNumber
            currentGame.setDetailsForRound(currentRoundNumber + 1, nextRoundDetails)

            val history = BidHistory()
            history.personToStart = losingPlayerNumber
            currentGame.setBidHistoryForRound(currentRoundNumber + 1, history)

            currentGame.roundNumber = currentRoundNumber + 1

            val newRoundNotification: String =
                XmlBuilderServer.factoryNewRoundNotification(
                    this,
                    nextRoundDetails,
                    losingPlayerNumber,
                )
            notifyAllUsersViaGameSocket(newRoundNotification, null, false)
        }
    }

    private fun dealHandsHashMap(
        hmHandSizeByPlayerNumber: ExtendedConcurrentHashMap<Int, Int>
    ): ConcurrentHashMap<Int, List<String>> {
        val hmHandByPlayerNumber: ConcurrentHashMap<Int, List<String>> = ConcurrentHashMap()

        val seed: Long = ServerGlobals.server.generateSeed()
        val deck =
            CardsUtil.createAndShuffleDeck(
                true,
                settings.jokerQuantity,
                settings.includeMoons,
                settings.includeStars,
                settings.negativeJacks,
                seed,
            )

        for (i in 0..<capacity) {
            val size: Int = hmHandSizeByPlayerNumber.getValue(i)
            val hand = (0..<size).map { deck.removeAt(0) }
            hmHandByPlayerNumber[i] = hand
        }

        return hmHandByPlayerNumber
    }

    private fun getWinningPlayer(hmHandSizeByPlayerNumber: ConcurrentHashMap<Int, Int>): Int {
        var activePlayers = 0
        var potentialWinner = 0

        for (i in 0..<capacity) {
            val handSize: Int = hmHandSizeByPlayerNumber.getValue(i)
            if (handSize > 0) {
                activePlayers++
                potentialWinner = i
            }
        }

        return if (activePlayers > 1) {
            -1
        } else {
            potentialWinner
        }
    }

    /**
     * Returns a set since it's possible for a player to be present as a player AND an observer.
     * This occurs if they've left but the game is still going - we keep the reference as a player
     * so others can't take the seat. They obviously then have the option to join as an observer.
     */
    private val allUsersInRoom: Set<String>
        get() = (currentPlayers + observers).toSet()

    val isGameInProgress: Boolean
        get() = currentPlayers.size == capacity

    val currentPlayerCount: Int
        get() = currentPlayers.size

    val observerCount: Int
        get() = observers.size

    fun getPlayer(playerNumber: Int): String? {
        return hmPlayerByPlayerNumber[playerNumber]
    }

    fun getFormerPlayer(playerNumber: Int): String? {
        return hmFormerPlayerByPlayerNumber[playerNumber]
    }

    private fun getGameForId(gameId: String): GameWrapper {
        if (gameId == currentGame.gameId) {
            return currentGame
        }

        if ((gameId == previousGame?.gameId)) {
            return previousGame!!
        }

        throw RuntimeException("Got a null game for room $name and gameId $gameId")
    }

    fun getNextGameForId(previousGameIdFromClient: String): GameWrapper {
        if (previousGameIdFromClient.isEmpty()) {
            return currentGame
        }

        if (previousGame == null) {
            logger.warn(
                "staleGameId",
                "Tried to get next game for gameId $previousGameIdFromClient but previous game was null. Current: ${currentGame.gameId}",
            )
            return currentGame
        }

        val previousGameId: String = previousGame!!.gameId
        if (previousGameId == previousGameIdFromClient) {
            return currentGame
        }

        logger.warn(
            "staleGameId",
            "Tried to get next game for gameId $previousGameIdFromClient but this did not match. Previous [${previousGame?.gameId}],  Current [${currentGame.gameId}]",
        )

        return currentGame
    }

    fun getLastBidForPlayer(playerNumber: Int, roundNumber: Int): Bid? {
        if (playerNumber == -1) {
            return null
        }

        val history: BidHistory = currentGame.getBidHistoryForRound(roundNumber)
        return history.getLastBidForPlayer(playerNumber)
    }

    fun addBidForPlayer(
        gameId: String,
        playerNumber: Int,
        roundNumber: Int,
        newBid: Bid?,
    ): Boolean {
        val game = getGameForId(gameId)

        val history: BidHistory = game.getBidHistoryForRound(roundNumber)
        val added: Boolean = history.addBidForPlayer(playerNumber, newBid)

        if (added) {
            // Notify all other capacity
            val bidNotification = XmlBuilderServer.getBidNotification(name, playerNumber, newBid)
            notifyAllUsersViaGameSocket(bidNotification, null, false)
        }

        return added
    }

    fun addToChatHistoryAndNotifyUsers(message: OnlineMessage) {
        if (isEmpty) {
            return
        }

        chatHistory.add(message)

        val chatMessage = XmlBuilderServer.getChatNotification(name, message)
        val uscs = uscStore.getAllForNames(allUsersInRoom)
        ServerGlobals.server.sendViaNotificationSocket(
            uscs,
            chatMessage,
            XmlConstants.SOCKET_NAME_CHAT,
            false,
        )
    }

    fun makeCopy() = Room(UUID.randomUUID(), baseName, settings, capacity, index + 1)

    override fun toString() = name
}
