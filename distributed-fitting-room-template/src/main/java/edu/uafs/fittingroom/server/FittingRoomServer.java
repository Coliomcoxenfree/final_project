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
import java.util.UUID; 
import edu.uafs.fittingroom.protocol.Message;

public class FittingRoomServer {
    private String serverId;
    private String coordHost;
    private int coordPort;
    private int listenPort;
    private int roomCount;
    private Room[] rooms;
    private int requestCount = 0;
    private static final long RESERVATION_TIMEOUT_MS = Long.getLong("reservation.timeout.ms", 5000L);

    public FittingRoomServer(String serverId, String coordHost, int coordPort, int listenPort, int roomCount) {
        this.serverId = serverId;
        this.coordHost = coordHost;
        this.coordPort = coordPort;
        this.listenPort = listenPort;
        this.roomCount = roomCount;
        this.rooms = new Room[roomCount];
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
                System.out.println(serverId + " reservation expired: room " + r.roomId + " (was held for " + r.ownerId + ")");
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
            System.out.println(serverId + " listening on port " + listenPort + " with " + roomCount + " rooms");
            registerWithCoordinator();
            startReservationReaper();
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleConnection(socket)).start();
            }
        } catch (IOException e) {
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
            System.out.println(serverId + " registered with the Coordinator (reply: " + Message.parse(reply).displayMessage() + ")");
        } catch (IOException e) {
            System.out.println("Could not reach the Coordinator: " + e.getMessage());
            System.exit(1);
        }
    }
// Reports current free room count back to the Coordinator
    private void reportCapacity() {
        try (Socket socket = new Socket(coordHost, coordPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println(new Message("CAPACITY_UPDATE", nextRequestId(), serverId, String.valueOf(freeRoomCount()), String.valueOf(roomCount)));
            in.readLine();
        } catch (IOException e) {
            System.out.println(serverId + " failed to report capacity: " + e.getMessage());
        }
    }

    // Method for reaper thread: every half second, reaper thread will free expired reservations and tell the Coordinator
    private void startReservationReaper() {
        Thread reaper = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                }catch (InterruptedException e) {
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
                    if ("RESERVE_REQUEST".equals(m.type)) {
                        String customerId = m.field(0);
                        Reservation res = reserveRoom(customerId);
                        Message reply;
                        if (res == null) {
                            reply = new Message("RESERVE_RESPONSE", m.requestId, serverId, "-1");
                            System.out.println(serverId + " has no room for " + customerId);
                        } else {
                            reply = new Message("RESERVE_RESPONSE", m.requestId, serverId, String.valueOf(res.roomId()), res.token(), String.valueOf(res.expiresAt()));
                            System.out.println(serverId + " reserved room " + res.roomId() + " for " + customerId);
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
                        System.out.println(customerId + " claims room " + roomId + " on " + serverId + ": " + (claimed ? "accepted" : "rejected"));
                        if (claimed) {
                            out.println(new Message("ACKNOWLEDGMENT", m.requestId, serverId, "claimed"));
                        } else { 
                            out.println(new Message("ERROR", m.requestId, serverId, "claim rejected: reservation invalid or expired"));
                        }
                    } else if ("RELEASE".equals(m.type)) {
                        String customerId = m.senderId;
                        int roomId = Integer.parseInt(m.field(0));
                        boolean released = releaseRoom(customerId, roomId);
                        System.out.println(serverId + " release of room " + roomId + " by " + customerId + ": " + released);
                        if (released) {
                            out.println(new Message("ACKNOWLEDGMENT", m.requestId, serverId, "released"));
                            reportCapacity();
                        } else {
                            out.println(new Message("ERROR", m.requestId, serverId, "release failed"));
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