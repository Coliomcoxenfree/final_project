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
import java.util.HashMap;
import java.util.Map;

import edu.uafs.fittingroom.protocol.Message;

public class FittingRoomServer {
    private String serverId;
    private String coordHost;
    private int coordPort;
    private int listenPort;
    private int roomCount;
    private Room[] rooms;
    private int requestCount = 0;
    private Map<String, Message> completedRequests = new HashMap<>();

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

    public synchronized int allocateRoom(String customerId) {
        for (Room r : rooms) {
            if (customerId.equals(r.ownerId)) {
                return r.roomId; //Prevents customers from taking multiple rooms at the same time
            }
        }
        for (Room r : rooms) {
            if (r.state == Room.RoomState.FREE) {
                r.state = Room.RoomState.OCCUPIED;
                r.ownerId = customerId;
                return r.roomId;
            }
        }

        return -1;
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

    // Reads msgs from client conn until it closes
    private void handleConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    Message m = Message.parse(line);
                    if (completedRequests.containsKey(m.requestId)) {
                        System.out.println("[request=" + m.requestId + "] Duplicate ignored " + serverId);
                        out.println(completedRequests.get(m.requestId));
                        continue;
                    }
                    if ("RESERVE_REQUEST".equals(m.type)) {
                        String customerId = m.field(0);
                        int allocatedRoomId = allocateRoom(customerId);
                        System.out.println("[request=" + m.requestId + "] " + serverId + " allocated room " + allocatedRoomId + " to " + customerId);
                        Message reply = new Message("RESERVE_RESPONSE", m.requestId, serverId, String.valueOf(allocatedRoomId));
                        completedRequests.put(m.requestId, reply); // save to map
                        out.println(reply);
                        if (allocatedRoomId != -1) {
                            reportCapacity();
                        }
                    } else if ("RELEASE".equals(m.type)) {
                        String customerId = m.field(0);
                        int roomId = Integer.parseInt(m.field(1));
                        boolean released = releaseRoom(customerId, roomId);
                        System.out.println("[request=" + m.requestId + "] " + serverId + " release of room " + roomId + " by " + customerId + ": " + released);
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