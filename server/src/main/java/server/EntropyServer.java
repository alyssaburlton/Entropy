package server;

import logging.LoggerUncaughtExceptionHandler;
import object.*;
import org.w3c.dom.Document;
import screen.DebugConsole;
import util.*;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.RSAPrivateKeySpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static utils.InjectedThings.logger;

public final class EntropyServer implements OnlineConstants {
    //Console
    private static DebugConsole console = new DebugConsole();

    //Caches
    private ExtendedConcurrentHashMap<String, UserConnection> hmUserConnectionByIpAndPort = new ExtendedConcurrentHashMap<>();
    private ConcurrentHashMap<String, Room> hmRoomByName = new ConcurrentHashMap<>();
    private ArrayList<OnlineMessage> lobbyMessages = new ArrayList<>();

    //Seed
    private static long currentSeedLong = 4613352884640512L;

    //Other
    private PrivateKey privateKey = null;

    public static void main() {
        EntropyServer server = new EntropyServer();
        Thread.setDefaultUncaughtExceptionHandler(new LoggerUncaughtExceptionHandler());

        //Initialise interfaces etc
        EncryptionUtil.setBase64Interface(new Base64Desktop());
        Debug.initialise(console);

        server.onStart();
    }

    private void onStart() {
        Debug.appendBanner("Start-Up");

        readInPrivateKey();
        registerDefaultRooms();

        Debug.append("Starting permanent threads");

        startInactiveCheckRunnable();
        startListenerThreads();

        Debug.appendBanner("Server is ready - accepting connections");
    }

    private void readInPrivateKey() {
        try (InputStream in = getClass().getResourceAsStream("/private.key");
             ObjectInputStream oin = new ObjectInputStream(new BufferedInputStream(in))) {
            BigInteger m = (BigInteger) oin.readObject();
            BigInteger e = (BigInteger) oin.readObject();
            RSAPrivateKeySpec keySpec = new RSAPrivateKeySpec(m, e);
            KeyFactory fact = KeyFactory.getInstance("RSA");
            privateKey = fact.generatePrivate(keySpec);
        } catch (Throwable e) {
            logger.error("keyError", "Failed to read in private key, server is borked :(", e);
        }
    }

    private void registerDefaultRooms() {
        Debug.append("Creating rooms...");

        //Entropy
        //2 player
        registerNewRoom("Potassium 1", 1, 2, 0, 2);
        registerNewRoom("Zinc 1", 1, 2, 2, 2);
        registerNewRoom("Helium 1", 1, 2, 4, 3);
        registerNewRoom("Magnesium 1", 1, 2, 2, 2, true, true, true, false, false);
        registerNewRoom("Cobalt 1", 1, 2, 0, 2, true, true, false, false, false);
        registerNewRoom("Chlorine 1", 1, 2, 0, 2, true, true, false, true, false);
        registerNewRoom("Gold 1", 1, 2, 0, 2, false, false, true, false, false);
        registerNewRoom("Lithium 1", 1, 2, 2, 2, false, false, false, true, true);
        registerNewRoom("Beryllium 1", 1, 2, 0, 2, true, true, false, false, true);

        //3 player
        registerNewRoom("Bromine 1", 1, 3, 0, 2);
        registerNewRoom("Argon 1", 1, 3, 2, 2);
        registerNewRoom("Hydrogen 1", 1, 3, 4, 3);
        registerNewRoom("Zirconium 1", 1, 3, 2, 2, true, true, true, false, false);
        registerNewRoom("Calcium 1", 1, 3, 0, 2, true, true, false, false, false);
        registerNewRoom("Iron 1", 1, 3, 2, 2, true, false, false, true, false);
        registerNewRoom("Palladium 1", 1, 3, 3, 3, true, true, true, false, true);

        //4 player
        registerNewRoom("Nickel 1", 1, 4, 0, 2);
        registerNewRoom("Sodium 1", 1, 4, 2, 2);
        registerNewRoom("Phosphorus 1", 1, 4, 4, 3);
        registerNewRoom("Titanium 1", 1, 4, 0, 2, true, true, false, true, false);
        registerNewRoom("Gallium 1", 1, 4, 2, 2, true, true, false, false, true);

        //Vectropy
        //2 player
        registerNewRoom("Oxygen 1", 2, 2, 0, 2);
        registerNewRoom("Neon 1", 2, 2, 2, 2);
        registerNewRoom("Copper 1", 2, 2, 4, 3);
        registerNewRoom("Manganese 1", 2, 2, 2, 2, true, true, true, false, false);
        registerNewRoom("Selenium 1", 2, 2, 0, 2, true, true, false, false, false);
        registerNewRoom("Chromium 1", 2, 2, 0, 2, true, true, false, true, false);
        registerNewRoom("Silver 1", 2, 2, 0, 2, false, false, true, false, false);
        registerNewRoom("Antimony 1", 2, 2, 2, 2, false, false, false, true, true);
        registerNewRoom("Tungsten 1", 2, 2, 0, 2, true, true, false, false, true);

        //3 player
        registerNewRoom("Carbon 1", 2, 3, 0, 2);
        registerNewRoom("Silicon 1", 2, 3, 2, 2);
        registerNewRoom("Nitrogen 1", 2, 3, 4, 3);
        registerNewRoom("Sulphur 1", 2, 3, 2, 2, true, true, true, false, false);
        registerNewRoom("Fluorine 1", 2, 3, 0, 2, true, true, false, false, false);
        registerNewRoom("Tin 1", 2, 3, 2, 2, true, false, false, true, false);
        registerNewRoom("Indium 1", 2, 3, 3, 3, true, true, true, false, true);

        //4 player
        registerNewRoom("Iodine 1", 2, 4, 0, 2);
        registerNewRoom("Lead 1", 2, 4, 2, 2);
        registerNewRoom("Uranium 1", 2, 4, 4, 3);
        registerNewRoom("Vanadium 1", 2, 4, 0, 2, true, true, false, true, false);
        registerNewRoom("Xenon 1", 2, 4, 2, 2, true, true, false, false, true);

        Debug.append("Finished creating " + hmRoomByName.size() + " rooms");
    }

    private void startInactiveCheckRunnable() {
        InactiveCheckRunnable runnable = new InactiveCheckRunnable(this);

        ServerThread inactiveCheckThread = new ServerThread(runnable, "InactiveCheck");
        inactiveCheckThread.start();
    }

    private void startListenerThreads() {
        int lowerBound = SERVER_PORT_NUMBER_LOWER_BOUND;
        int upperBound = SERVER_PORT_NUMBER_UPPER_BOUND;

        for (int i = lowerBound; i < upperBound; i++) {
            try {
                ServerThread listenerThread = new ServerThread(new MessageListener(this, i));
                listenerThread.setName("Listener-" + i);
                listenerThread.start();
            } catch (Throwable t) {
                logger.error("listenerError", "Unable to start listener thread on port " + i, t);
            }
        }
    }

    public void executeInWorkerPool(ServerRunnable runnable) {
        Globals.workerPool.executeServerRunnable(runnable);
    }

    public boolean isAlreadyOnline(String username) {
        if (username.equalsIgnoreCase("Admin")) {
            return true;
        }

        return getUserConnectionForUsername(username) != null;
    }

    public ArrayList<UserConnection> getUserConnectionsForUsernames(HashSet<String> usernames) {
        ArrayList<UserConnection> uscs = new ArrayList<>();

        Iterator<String> it = usernames.iterator();
        for (; it.hasNext(); ) {
            String username = it.next();
            UserConnection usc = getUserConnectionForUsername(username);
            if (usc != null) {
                uscs.add(usc);
            }
        }

        return uscs;
    }

    public UserConnection getUserConnectionForUsername(String username) {
        Iterator<String> it = hmUserConnectionByIpAndPort.keySet().iterator();
        for (; it.hasNext(); ) {
            String ip = it.next();
            UserConnection usc = hmUserConnectionByIpAndPort.get(ip);
            String usernameOnUsc = usc.getUsername();
            if (usernameOnUsc != null
                    && usernameOnUsc.equals(username)) {
                return usc;
            }
        }

        return null;
    }

    public void removeFromUsersOnline(UserConnection usc) {
        //Null these out so we don't try to send any more notifications
        usc.destroyNotificationSockets();

        //Need to remove them from rooms too
        String username = usc.getUsername();
        if (username != null) {
            ColourGenerator.freeUpColour(usc.getColour());

            List<Room> rooms = getRooms();
            for (int i = 0; i < rooms.size(); i++) {
                Room room = rooms.get(i);
                room.removeFromObservers(username);
                room.removePlayer(username, false);
            }

            Debug.append(username + " has disconnected");
        }

        //Now remove the user connection.
        hmUserConnectionByIpAndPort.removeAllWithValue(usc);
        if (hmUserConnectionByIpAndPort.isEmpty()
                && username != null) {
            resetLobby();
            return;
        }

        lobbyChanged();
    }

    private void resetLobby() {
        int countRemoved = 0;

        List<Room> rooms = getRooms();
        int size = rooms.size();
        for (int i = 0; i < size; i++) {
            Room room = rooms.get(i);
            if (room.getIsCopy()) {
                String roomName = room.getRoomName();
                hmRoomByName.remove(roomName);
                countRemoved++;
            }
        }

        //Log out if we've actually removed some rooms
        if (countRemoved > 0) {
            Debug.append("Removed " + countRemoved + " excess rooms");
        }

        Debug.append("Cleared lobby messages");
        lobbyMessages.clear();
    }

    public void addToChatHistory(String id, String message, String colour, String username) {
        OnlineMessage messageObj = new OnlineMessage(colour, message, username);
        addToChatHistory(id, messageObj);
    }

    public void addAdminMessage(String message) {
        OnlineMessage messageObj = new OnlineMessage("black", message, "Admin");
        Iterator<String> it = hmRoomByName.keySet().iterator();
        for (; it.hasNext(); ) {
            String name = it.next();
            addToChatHistory(name, messageObj);
        }

        addToChatHistory(LOBBY_ID, messageObj);
    }

    private void addToChatHistory(String name, OnlineMessage message) {
        if (name.equals(LOBBY_ID)) {
            lobbyMessages.add(message);
            ArrayList<UserConnection> usersToNotify = getUserConnections(true);

            Document chatMessage = XmlBuilderServer.getChatNotification(name, message);
            sendViaNotificationSocket(usersToNotify, chatMessage, XmlConstants.SOCKET_NAME_CHAT);
        } else {
            Room room = hmRoomByName.get(name);
            room.addToChatHistoryAndNotifyUsers(message);
        }
    }

    public void lobbyChanged() {
        lobbyChanged(null);
    }

    public void lobbyChanged(UserConnection userToExclude) {
        ArrayList<UserConnection> usersToNotify = getUserConnections(true);
        if (userToExclude != null) {
            usersToNotify.remove(userToExclude);
        }

        Document lobbyMessage = XmlUtil.factoryNewDocument();
        XmlBuilderServer.appendLobbyResponse(lobbyMessage, this);

        sendViaNotificationSocket(usersToNotify, lobbyMessage, XmlConstants.SOCKET_NAME_LOBBY);
    }

    private void sendViaNotificationSocket(ArrayList<UserConnection> uscs, Document message, String socketName) {
        sendViaNotificationSocket(uscs, message, socketName, false);
    }

    public void sendViaNotificationSocket(ArrayList<UserConnection> uscs, Document message, String socketName, boolean blocking) {
        AtomicInteger counter = null;
        if (blocking) {
            int size = uscs.size();
            counter = new AtomicInteger(size);
        }

        sendViaNotificationSocket(uscs, message, socketName, counter);
    }

    private void sendViaNotificationSocket(ArrayList<UserConnection> uscs, Document message, String socketName, AtomicInteger counter) {
        int size = uscs.size();
        for (int i = 0; i < size; i++) {
            UserConnection usc = uscs.get(i);
            usc.sendNotificationInWorkerPool(message, this, socketName, counter);
        }

        if (counter != null) {
            while (counter.get() > 0) {
                //So you wait. You waiiiit. Yoouuu waiiiiiiit.
                try {
                    Thread.sleep(500);
                } catch (Throwable t) {
                }
            }
        }
    }

    public List<OnlineMessage> getChatHistory(String id) {
        if (id.equals(LOBBY_ID)) {
            return lobbyMessages;
        }

        Room room = hmRoomByName.get(id);
        return room.getChatHistory();
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    private void registerNewRoom(String roomName, int mode, int players, int jokerQuantity, int jokerValue) {
        registerNewRoom(roomName, mode, players, jokerQuantity, jokerValue, false, false, false, false, false);
    }

    private Room registerNewRoom(String roomName, int mode, int players, int jokerQuantity, int jokerValue,
                                 boolean includeMoons, boolean includeStars, boolean illegalAllowed, boolean negativeJacks,
                                 boolean cardReveal) {
        Iterator<String> it = hmRoomByName.keySet().iterator();
        for (; it.hasNext(); ) {
            String id = it.next();
            Room room = hmRoomByName.get(id);

            String nameToCheck = room.getRoomName();
            if (nameToCheck.equals(roomName)) {
                Debug.append("Not creating room " + nameToCheck + " as a room with that name already exists.");
                return null;
            }
        }

        Room room = new Room(roomName, mode, players, this);
        room.setJokerQuantity(jokerQuantity);
        room.setJokerValue(jokerValue);
        room.setIncludeMoons(includeMoons);
        room.setIncludeStars(includeStars);
        room.setIllegalAllowed(illegalAllowed);
        room.setNegativeJacks(negativeJacks);
        room.setCardReveal(cardReveal);
        room.initialiseGame();
        hmRoomByName.put(roomName, room);

        Debug.append("Room created: " + roomName);
        return room;
    }

    public void registerCopy(Room room) {
        String roomName = room.getRoomName();
        int mode = room.getMode();
        int players = room.getPlayers();
        int jokerQuantity = room.getJokerQuantity();
        int jokerValue = room.getJokerValue();
        boolean includeMoons = room.getIncludeMoons();
        boolean includeStars = room.getIncludeStars();
        boolean illegalAllowed = room.getIllegalAllowed();
        boolean negativeJacks = room.getNegativeJacks();
        boolean cardReveal = room.getCardReveal();

        int index = roomName.indexOf(' ') + 1;
        String roomNumberStr = roomName.substring(index);
        int roomNumber = Integer.parseInt(roomNumberStr);

        String newRoomName = roomName.substring(0, index) + (roomNumber + 1);

        Room newRoom = registerNewRoom(newRoomName, mode, players, jokerQuantity, jokerValue,
                includeMoons, includeStars, illegalAllowed, negativeJacks, cardReveal);

        if (newRoom != null) {
            newRoom.setIsCopy(true);
            lobbyChanged();
        }
    }

    public ArrayList<Room> getRooms() {
        ArrayList<Room> list = new ArrayList<>();
        Iterator<String> it = hmRoomByName.keySet().iterator();

        for (; it.hasNext(); ) {
            String id = it.next();
            Room room = hmRoomByName.get(id);
            list.add(room);
        }

        return list;
    }

    public Room getRoomForName(String name) {
        return hmRoomByName.get(name);
    }

    /**
     * Generate a secure seed based on:
     * - Current time in nanos
     * - A private seed known to the Server which is incremented on each hit
     */
    public long generateSeed() {
        currentSeedLong++;

        return System.nanoTime() + currentSeedLong;
    }

    public ArrayList<UserConnection> getUserConnections(boolean onlyLoggedIn) {
        ArrayList<UserConnection> uscs = new ArrayList<>();

        Iterator<String> it = hmUserConnectionByIpAndPort.keySet().iterator();
        for (; it.hasNext(); ) {
            String ip = it.next();
            UserConnection usc = hmUserConnectionByIpAndPort.get(ip);
            if (onlyLoggedIn
                    && usc.getUsername() == null) {
                continue;
            }

            uscs.add(usc);
        }

        return uscs;
    }

    public UserConnection getUserConnectionForIpAndPort(String ipAndPort) {
        return hmUserConnectionByIpAndPort.get(ipAndPort);
    }

    public void setUserConnectionForIpAndPort(String ipAndPort, UserConnection usc) {
        hmUserConnectionByIpAndPort.put(ipAndPort, usc);
    }

    public ArrayList<String> getAllPortsCurrentlyUsedByIp(String ipAddress) {
        Iterator<String> it = hmUserConnectionByIpAndPort.keySet().iterator();
        ArrayList<String> list = new ArrayList<>();

        for (; it.hasNext(); ) {
            String ipAndPort = it.next();
            int index = ipAndPort.indexOf("_");
            String ipToCheck = ipAndPort.substring(0, index);
            if (ipToCheck.equals(ipAddress)) {
                String port = ipAndPort.substring(index + 1);
                list.add(port);
            }
        }

        return list;
    }
}
