package util;

import object.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import room.Room;
import server.EntropyServer;

import java.util.List;

public class XmlBuilderServer implements XmlConstants
{
	//Cached acknowledgements
	//AJH 28 Feb 2015 - Made these final - they don't change
	private static final Document ACKNOWLEDGEMENT = XmlUtil.factorySimpleMessage(RESPONSE_TAG_ACKNOWLEDGEMENT);
	
	public static Document getAcknowledgement()
	{
		return ACKNOWLEDGEMENT;
	}

	public static Document getLeaderboardResponse(List<Room> rooms) throws Throwable
	{
		Document response = XmlUtil.factoryNewDocument();
		Element rootElement = response.createElement(RESPONSE_TAG_LEADERBOARD);
		
		addGlobalStats(response, rootElement, rooms);
		
		String[] usernames = {};
		
		int length = usernames.length;
		for (int i=0; i<length; i++)
		{
			String username = usernames[i];
			if (username.contains("ForceReset"))
			{
				continue;
			}
			
			Element userElement = response.createElement("User");
			addWinsAndLossesToElement(userElement, username);
			
			rootElement.appendChild(userElement);
		}
		
		response.appendChild(rootElement);
		return response;
	}
	
	private static void addWinsAndLossesToElement(Element userElement, String username)
	{
		userElement.setAttribute("Username", username);
		userElement.setAttribute("Entropy2Lost", "0");
		userElement.setAttribute("Entropy3Lost", "0");
		userElement.setAttribute("Entropy4Lost", "0");
		userElement.setAttribute("Entropy2Won", "0");
		userElement.setAttribute("Entropy3Won", "0");
		userElement.setAttribute("Entropy4Won", "0");
		
		userElement.setAttribute("Vectropy2Lost", "0");
		userElement.setAttribute("Vectropy3Lost", "0");
		userElement.setAttribute("Vectropy4Lost", "0");
		userElement.setAttribute("Vectropy2Won", "0");
		userElement.setAttribute("Vectropy3Won", "0");
		userElement.setAttribute("Vectropy4Won", "0");
	}
	
	private static void addGlobalStats(Document response, Element rootElement, List<Room> rooms)
	{
		rootElement.setAttribute("TotalGames", "0");
		rootElement.setAttribute("TotalDuration", "0");
		rootElement.setAttribute("UsersOnline", "0" );
		
		addRoomStats(response, rootElement, rooms);
	}
	
	private static void addRoomStats(Document response, Element root, List<Room> rooms)
	{
		for (Room room : rooms) {
			Element roomStatsElement = response.createElement("RoomStats");

			roomStatsElement.setAttribute("RoomName", room.getName());
			roomStatsElement.setAttribute("RoomGamesPlayed", "0");
			roomStatsElement.setAttribute("RoomDuration", "0");
			root.appendChild(roomStatsElement);
		}
	}
	
	public static Document factoryStatisticsNotification(String username)
	{
		Document response = XmlUtil.factoryNewDocument();
		Element statsElement = createStatisticsElement(response, username);
		response.appendChild(statsElement);
		return response;
	}
	
	private static Document appendStatisticsResponse(Document response, String username)
	{
		Element rootElement = response.getDocumentElement();
		Element statsElement = createStatisticsElement(response, username);
		
		rootElement.appendChild(statsElement);
		return response;
	}
	
	private static Element createStatisticsElement(Document response, String username)
	{
		Element statsElement = response.createElement(RESPONSE_TAG_STATISTICS_NOTIFICATION);
		addWinsAndLossesToElement(statsElement, username);
		
		return statsElement;
	}
	
	public static String getChatNotification(String roomName, OnlineMessage chatMessage)
	{
		Document notification = XmlUtil.factoryNewDocument();
		Element root = notification.createElement(RESPONSE_TAG_CHAT_NOTIFICATION);
		root.setAttribute("RoomName", roomName);
		
		appendChatMessage(notification, root, chatMessage, -1);
		
		notification.appendChild(root);
		return XmlUtil.getStringFromDocument(notification);
	}
	
	private static void appendCurrentChatElement(Document response, String roomName, EntropyServer server)
	{
		Element root = response.getDocumentElement();
		Element rootChatElement = response.createElement(RESPONSE_TAG_CHAT_NOTIFICATION);
		rootChatElement.setAttribute("RoomName", roomName);

		List<OnlineMessage> onlineMessages = server.getChatHistory(roomName);
		int size = onlineMessages.size();
		int startIndex = 0;
		if (size > 10)
		{
			startIndex = size - 10;
		}
		
		appendChatMessages(response, rootChatElement, onlineMessages, startIndex);
		root.appendChild(rootChatElement);
	}
	
	private static void appendChatMessages(Document response, Element root, List<OnlineMessage> chatHistory, 
	  int startIndex)
	{
		for (int i=startIndex; i<chatHistory.size(); i++)
		{
			OnlineMessage message = chatHistory.get(i);
			int index = i - startIndex;
			appendChatMessage(response, root, message, index);
		}
	}
	
	private static void appendChatMessage(Document response, Element root, OnlineMessage message, int index)
	{
		Element messageElement = response.createElement("Message");
		
		if (index > -1)
		{
			messageElement.setAttribute("Index", "" + index);
		}
		
		messageElement.setAttribute("MessageText", message.getText());
		messageElement.setAttribute("Colour", message.getColour());
		messageElement.setAttribute("Username", message.getUsername());
		
		root.appendChild(messageElement);
	}
	
	public static Document getRoomJoinResponse(Room room, String username, String observerStr, int playerNumber, EntropyServer server)
	{
		Document response = XmlUtil.factoryNewDocument();
		Element rootElement = response.createElement(RESPONSE_TAG_JOIN_ROOM_RESPONSE);
		rootElement.setAttribute("RoomId", room.getName());
		
		boolean observer = !observerStr.isEmpty();
		if (observer)
		{
			room.addToObservers(username);
			rootElement.setAttribute("Observer", "true");
		}
		else
		{
			if (!room.isFull())
			{
				playerNumber = room.attemptToJoinAsPlayer(username, playerNumber);
				rootElement.setAttribute("PlayerNumber", "" + playerNumber);

				if (room.isFull())
				{
					ServerGlobals.INSTANCE.getRoomStore().addCopy(room);
				}
			}
			else
			{
				rootElement.setAttribute("RoomFull", "true");
			}
		}
		
		response.appendChild(rootElement);
		
		//Append the current chat history and players for this room
		appendCurrentChatElement(response, room.getName(), server);
		addPlayers(room, rootElement);
		
		if (observer)
		{
			addFormerPlayers(room, rootElement);
		}
		
		return response;
	}
	
	public static Document getCloseRoomResponse(Room room, String username) 
	{
		Document response = XmlUtil.factoryNewDocument();
		Element rootElement = response.createElement(RESPONSE_TAG_CLOSE_ROOM_RESPONSE);
		rootElement.setAttribute("RoomId", room.getName());

		room.removeFromObservers(username);
		room.removePlayer(username, true);
		
		response.appendChild(rootElement);
		return response;
	}
	
	public static String getPlayerNotification(Room room)
	{
		Document response = XmlUtil.factoryNewDocument();
		Element rootElement = response.createElement(RESPONSE_TAG_PLAYER_NOTIFICATION);
		rootElement.setAttribute("RoomName", room.getName());
		
		addPlayers(room, rootElement);
		
		response.appendChild(rootElement);
		return XmlUtil.getStringFromDocument(response);
	}
	
	public static Document getObserverResponse(Room room)
	{
		Document response = XmlUtil.factoryNewDocument();
		Element rootElement = response.createElement(RESPONSE_TAG_OBSERVER_RESPONSE);
		rootElement.setAttribute("RoomId", room.getName());
		
		GameWrapper game = room.getNextGameForId("");
		long countdownTimeRemaining = game.getCountdownTimeRemaining();
		
		if (countdownTimeRemaining > 0)
		{
			rootElement.setAttribute("CountdownTimeRemaining", "" + countdownTimeRemaining);
		}
		else if (room.isGameInProgress())
		{
			XmlUtil.setAttributeBoolean(rootElement, "GameInProgress", true);
			
			//Set the game start millis as it's been requested
			game.setGameStartMillisIfUnset(System.currentTimeMillis());
			
			int roundNumber = game.getRoundNumber();
			BidHistory history = game.getBidHistoryForRound(roundNumber);
			
			rootElement.setAttribute("GameId", game.getGameId());
			rootElement.setAttribute("RoundNumber", "" + roundNumber);
			rootElement.setAttribute("PersonToStart", "" + history.getPersonToStart());
			
			addPlayerHands(room, response, rootElement, game, roundNumber);
			
			int lastPlayerToAct = history.getLastPlayerToAct();
			rootElement.setAttribute("LastPlayerToAct", "" + lastPlayerToAct);
			
			addLastBids(room, roundNumber, rootElement);
		}
		
		response.appendChild(rootElement);
		return response;
	}
	private static void addPlayers(Room room, Element rootElement)
	{
		int numberOfPlayers = room.getCapacity();
		for (int i=0; i<numberOfPlayers; i++)
		{
			String username = room.getPlayer(i);
			if (username != null)
			{
				rootElement.setAttribute("Player-" + i, username);
			}
		}
	}
	private static void addFormerPlayers(Room room, Element rootElement)
	{
		int numberOfPlayers = room.getCapacity();
		for (int i=0; i<numberOfPlayers; i++)
		{
			String username = room.getFormerPlayer(i);
			if (username != null)
			{
				rootElement.setAttribute("FormerPlayer-" + i, username);
			}
		}
	}
	private static void addPlayerHands(Room room, Document response, Element rootElement, GameWrapper game, int roundNumber)
	{
		HandDetails details = game.getDetailsForRound(roundNumber);
		
		int numberOfPlayers = room.getCapacity();
		for (int i=0; i<numberOfPlayers; i++)
		{
			Element handElement = response.createElement("Hand");
			handElement.setAttribute("PlayerNumber", "" + i);
			
			List<String> hand = details.getHand(i);
			for (int j=0; j<hand.size(); j++)
			{
				handElement.setAttribute("Card-" + j, hand.get(j));
			}
			rootElement.appendChild(handElement);
		}
	}
	private static void addLastBids(Room room, int roundNumber, Element rootElement)
	{
		int numberOfPlayers = room.getCapacity();
		for (int i=0; i<numberOfPlayers; i++)
		{
			Bid bid = room.getLastBidForPlayer(i, roundNumber);
			if (bid != null)
			{
				rootElement.setAttribute("LastBid-" + i, bid.toXmlString());
			}
		}
	}
	
	public static Document getNewGameResponse(Room room, String previousGameId)
	{
		Document response = XmlUtil.factoryNewDocument();
		Element rootElement = response.createElement(RESPONSE_TAG_NEW_GAME);
		
		rootElement.setAttribute("RoomId", room.getName());
		
		if (!room.isFull())
		{
			rootElement.setAttribute("WaitingForPlayers", "true");
			response.appendChild(rootElement);
			return response;
		}
		
		GameWrapper nextGame = room.getNextGameForId(previousGameId);
		nextGame.setCountdownStartMillisIfUnset();
		
		long countdownTimeRemaining = nextGame.getCountdownTimeRemaining();
		if (countdownTimeRemaining > 0)
		{
			rootElement.setAttribute("CountdownTimeRemaining", "" + countdownTimeRemaining);
			response.appendChild(rootElement);
			return response;
		}
		
		//Set the game start millis as it's been requested
		nextGame.setGameStartMillisIfUnset(System.currentTimeMillis());
		
		rootElement.setAttribute("GameId", nextGame.getGameId());
		addPlayerHands(room, response, rootElement, nextGame, 1);
		rootElement.setAttribute("PersonToStart", "" + nextGame.getPersonToStart(1));
		
		response.appendChild(rootElement);
		return response;
	}
	
	public static Document getBidAck(Room room, String gameId, int roundNumber, String bidStr, int previousBidder)
	{
		boolean includeMoons = room.getSettings().getIncludeMoons();
		boolean includeStars = room.getSettings().getIncludeStars();
		
		Bid bid = Bid.factoryFromXmlString(bidStr, includeMoons, includeStars);
		int playerNumber = bid.getPlayer().getPlayerNumber();
		boolean added = room.addBidForPlayer(gameId, playerNumber, roundNumber, bid);
		
		Bid previousBid = room.getLastBidForPlayer(previousBidder, roundNumber);
		if (bid.isChallenge()
		  && added)
		{
			room.handleChallenge(gameId, roundNumber, playerNumber, previousBidder, previousBid);
		}
		else if (bid.isIllegal()
		  && added)
		{
			room.handleIllegal(gameId, roundNumber, playerNumber, previousBidder, previousBid);
		}

		return ACKNOWLEDGEMENT;
	}
	
	public static String getBidNotification(String roomName, int playerNumber, Bid bid)
	{
		Document response = XmlUtil.factoryNewDocument();
		Element rootElement = response.createElement(RESPONSE_TAG_BID_NOTIFICATION);
		
		rootElement.setAttribute("RoomName", roomName);
		rootElement.setAttribute("PlayerNumber", "" + playerNumber);
		rootElement.setAttribute("Bid", bid.toXmlString());
		
		response.appendChild(rootElement);
		return XmlUtil.getStringFromDocument(response);
	}
	
	public static String factoryGameOverNotification(Room room, int winningPlayer)
	{
		Document response = XmlUtil.factoryNewDocument();
		Element rootElement = response.createElement(RESPONSE_TAG_GAME_OVER_NOTIFICATION);
		
		rootElement.setAttribute("WinningPlayer", "" + winningPlayer);
		rootElement.setAttribute("RoomName", room.getName());
		
		response.appendChild(rootElement);
		return XmlUtil.getStringFromDocument(response);
	}
	
	public static String factoryNewRoundNotification(Room room, HandDetails details, int personToStart)
	{
		Document response = XmlUtil.factoryNewDocument();
		Element rootElement = response.createElement(RESPONSE_TAG_NEW_ROUND_NOTIFICATION);
		rootElement.setAttribute("RoomName", room.getName());
		
		int numberOfPlayers = room.getCapacity();
		for (int i=0; i<numberOfPlayers; i++)
		{
			List<String> hand = details.getHand(i);
			if (hand.isEmpty())
			{
				continue;
			}
			
			Element handElement = response.createElement("Hand");
			handElement.setAttribute("PlayerNumber", "" + i);
			for (int j=0; j<hand.size(); j++)
			{
				handElement.setAttribute("Card-" + j, hand.get(j));
			}
			rootElement.appendChild(handElement);
		}
		
		rootElement.setAttribute("PersonToStart", "" + personToStart);
		
		response.appendChild(rootElement);
		return XmlUtil.getStringFromDocument(response);
	}
	
	public static Document getStackTraceResponse()
	{
		Document response = XmlUtil.factoryNewDocument();
		Element rootElement = response.createElement(RESPONSE_TAG_STACK_TRACE);
		
		response.appendChild(rootElement);
		return response;
	}
	
	public static Document getSocketTimeOutResponse()
	{
		Document response = XmlUtil.factoryNewDocument();
		Element rootElement = response.createElement(RESPONSE_TAG_SOCKET_TIME_OUT);
		
		response.appendChild(rootElement);
		return response;
	}
}
