/********************************
Name: Amy, Brittany, Cole, Thomas
Problem Set: Final Group Project
Due Date: December 11, 2026
********************************/

package edu.uafs.fittingroom.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import edu.uafs.fittingroom.protocol.Message;
import edu.uafs.fittingroom.protocol.Logger;

public class FittingRoomServer {
    private String serverId;
    private String coordHost;
    private int coordPort;
    private int listenPort;
    private int roomCount;
    private Room[] rooms;
    private int requestCount = 0;
    private Map<String, Message> completedRequests = new ConcurrentHashMap<>(); // replies already sent, by request id
    private static final long RESERVATION_TIMEOUT_MS = Long.getLong("reservation.timeout.ms", 5000L);
    private Logger logger;

    public FittingRoomServer(String serverId, String coordHost, int coordPort, int listenPort, int roomCount) {
        this.serverId = serverId;
        this.coordHost = coordHost;
        this.coordPort = coordPort;
        this.listenPort = listenPort;
        this.roomCount = roomCount;
        this.rooms = new Room[roomCount];

        logger = new Logger(serverId, serverId + ".log");

        for(int i = 0; i < roomCount; i++) {
            rooms[i] = new Room(i + 1);
        }
    }

    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Use: java FittingRoomServer <serverId> <coordHost> <coordPort> <listenPort> <rooms>");
            return;
        }

        String serverId = args[0];
        String coordHost = args[1];
        int coordPort = Integer.parseInt(args[2]);
        int listenPort = Integer.parseInt(args[3]);
        int roomCount = Integer.parseInt(args[4]);
        new FittingRoomServer(serverId, coordHost, coordPort, listenPort, roomCount).start();
    }

    public synchronized Reservation reserveRoom(String customerId) {
        for (Room r : rooms) {
            if (customerId.equals(r.ownerId)) {
                if (r.state == Room.RoomState.RESERVED) {
                    return new Reservation(r.roomId, r.token, r.expiresAt); // returns the same reservation since client already has a room reserved 
                }
                return null; // returns null since cleint is already in a room
            }
        }

        for (Room r : rooms) {
            if (r.state == Room.RoomState.FREE) {
                r.state = Room.RoomState.RESERVED;
                r.ownerId = customerId;
                r.token = UUID.randomUUID().toString();
                r.expiresAt = System.currentTimeMillis() + RESERVATION_TIMEOUT_MS;
                return new Reservation(r.roomId, r.token, r.expiresAt);
            }
        }
        return null;
    }

    public synchronized boolean claimRoom(String customerId, int roomId, String token) {
        if (roomId < 1 || roomId > rooms.length) {
            return false;
        }
        Room r = rooms[roomId - 1];
        if (r.state == Room.RoomState.RESERVED && customerId.equals(r.ownerId) && token.equals(r.token) && System.currentTimeMillis() < r.expiresAt) {
            r.state = Room.RoomState.OCCUPIED;
            r.token = null;
            return true;
        }
        return false;
    }

    public synchronized int expireReservations() {
        long now = System.currentTimeMillis();
        int freed = 0;
        for (Room r : rooms) {
            if (r.state == Room.RoomState.RESERVED && now >= r.expiresAt) {
                logger.log("RESERVATION_EXPIRED", r.ownerId, serverId, "-", serverId + " reservation expired: Room " + r.roomId + " (was held for " + r.ownerId + ")");
                r.state = Room.RoomState.FREE;
                r.ownerId = null;
                r.token = null;
                r.expiresAt = 0;
                freed++;
            }
        }
        return freed;
    }

    public synchronized boolean releaseRoom(String customerId, int roomId) {
        if (roomId < 1 || roomId > rooms.length) {
            return false;
        }
        Room r = rooms[roomId -1]; 
        if (r.state == Room.RoomState.OCCUPIED && customerId.equals(r.ownerId)) {
            r.state = Room.RoomState.FREE;
            r.ownerId = null;
            return true;
        }

        return false;
    }

    public synchronized int freeRoomCount() {
        int count = 0;
        for (Room r : rooms) {
            if (r.state  == Room.RoomState.FREE) {
                count++;
            }
        }

        return count;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
            logger.log("STARTED", "-", serverId, "-", serverId + " listening on port " + listenPort + " with " + roomCount + " rooms");
            registerWithCoordinator();
            startReservationReaper();
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleConnection(socket)).start();
            }
        } catch (IOException e) {
            logger.log("SERVER_ERROR", "-", serverId, "-", serverId + " error: " + e.getMessage());
            System.out.println(serverId + " error: " + e.getMessage());
        }
    }

    //Each req gets next num
    private synchronized String nextRequestId() {
        requestCount++;
        return serverId + "-" + requestCount;
    }

    //Sends reg to coordinator
    private void registerWithCoordinator() {
        String myHost = "localhost";
        try {
            myHost = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            //"localhost"
        }

        try (Socket socket = new Socket(coordHost, coordPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println(new Message("REGISTER", nextRequestId(), serverId, myHost, String.valueOf(listenPort), String.valueOf(roomCount)));
            String reply = in.readLine();
            logger.log("SERVER_REGISTERED", "-", serverId, serverId + "-1", serverId + " registered with the Coordinator (reply: " + Message.parse(reply).displayMessage() + ")");
        } catch (IOException e) {
            System.out.println("Could not reach the Coordinator: " + e.getMessage());
            logger.log("COORDINATOR_CONNECTION_FAILED", "-", serverId, "-", "Could not reach the Coordinator: " + e.getMessage());
            System.exit(1);
        }
    }
// Reports current free room count back to the Coordinator
    private void reportCapacity() {
        String requestId = nextRequestId();
        try (Socket socket = new Socket(coordHost, coordPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println(new Message("CAPACITY_UPDATE", requestId, serverId, String.valueOf(freeRoomCount()), String.valueOf(roomCount)));
            in.readLine();
            logger.log("CAPACITY_REPORTED", "-", serverId, requestId, serverId + " reported capacity: " + freeRoomCount() + "/" + roomCount + " free");
        } catch (IOException e) {
            logger.log("CAPACITY_REPORT_FAILED", "-", serverId, requestId, serverId + " failed to report capacity: " + e.getMessage());
        }
    }

    // Method for reaper thread: every half second, reaper thread will free expired reservations and tell the Coordinator
    private void startReservationReaper() {
        Thread reaper = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
                int expiredReservations = expireReservations();
                if (expiredReservations > 0) {
                    reportCapacity();
                }
            }
        });
        reaper.setDaemon(true);
        reaper.start();
    }

    // Reads msgs from client conn until it closes
    private void handleConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    Message m = Message.parse(line);
                    if (completedRequests.containsKey(m.requestId)) {
                        logger.log("DUPLICATE_REQUEST", m.senderId, serverId, m.requestId, "Duplicate request ignored by " + serverId);
                        out.println(completedRequests.get(m.requestId));
                        continue;
                    }
                    if ("RESERVE_REQUEST".equals(m.type)) {
                        String customerId = m.field(0);
                        Reservation res = reserveRoom(customerId);
                        Message reply;
                        if (res == null) {
                            reply = new Message("RESERVE_RESPONSE", m.requestId, serverId, "-1");
                            logger.log("RESERVATION_FAILED", customerId, serverId, m.requestId, serverId + " has no room for " + customerId);
                        } else {
                            reply = new Message("RESERVE_RESPONSE", m.requestId, serverId, String.valueOf(res.roomId()), res.token(), String.valueOf(res.expiresAt()));
                            completedRequests.put(m.requestId, reply); // only successful reservations are cached
                            logger.log("ROOM_RESERVED", customerId, serverId, m.requestId, serverId + " reserved room " + res.roomId() + " for " + customerId);
                        }
                        out.println(reply);
                        if (res != null) {
                            reportCapacity();
                        }
                    } else if ("CLAIM".equals(m.type)) {
                        String customerId = m.senderId;
                        int roomId = Integer.parseInt(m.field(0));
                        String token = m.field(1);
                        boolean claimed = claimRoom(customerId, roomId, token);
                        logger.log(claimed ? "ROOM_CLAIMED" : "ROOM_CLAIM_REJECTED", customerId, serverId, m.requestId, customerId + " claims room " + roomId + " on " + serverId + ": " + (claimed ? "accepted" : "rejected"));
                        Message reply;
                        if (claimed) {
                            reply = new Message("ACKNOWLEDGMENT", m.requestId, serverId, "claimed");
                        } else {
                            reply = new Message("ERROR", m.requestId, serverId, "claim rejected: reservation invalid or expired");
                        }
                        completedRequests.put(m.requestId, reply);
                        out.println(reply);
                    } else if ("RELEASE".equals(m.type)) {
                        String customerId = m.senderId;
                        int roomId = Integer.parseInt(m.field(0));
                        boolean released = releaseRoom(customerId, roomId);
                        logger.log(released ? "ROOM_RELEASED" : "ROOM_RELEASE_FAILED", customerId, serverId, m.requestId, serverId + " release of room " + roomId + " by " + customerId + ": " + released);
                        Message reply;
                        if (released) {
                            reply = new Message("ACKNOWLEDGMENT", m.requestId, serverId, "released");
                            completedRequests.put(m.requestId, reply);
                            out.println(reply);
                            reportCapacity();
                        } else {
                            reply = new Message("ERROR", m.requestId, serverId, "release failed");
                            completedRequests.put(m.requestId, reply);
                            out.println(reply);
                        }
                    } else {
                        out.println(new Message("ERROR", m.requestId, serverId, "unknown message type " + m.type));
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("Rejected message: " + e.getMessage());
                    out.println(new Message("ERROR", "-", serverId, e.getMessage()));
                }
            }
        } catch (IOException e) {
            System.out.println("Connection closed: " + e.getMessage());
        }
    }
}