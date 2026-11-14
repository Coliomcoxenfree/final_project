/********************************
Name: Amy, Brittany, Cole, Thomas
Problem Set: Final Group Project
Due Date: December 11, 2026
********************************/

package edu.uafs.fittingroom.coordinator;

import edu.uafs.fittingroom.protocol.Message;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedList;
import java.util.Queue;

public class Coordinator {
    private int port;
    private int waitingCapacity; // 2 x total rooms
    private Map<String, ServerInfo> servers = new HashMap<>();
    private Queue<Message> waitingQueue = new LinkedList<>();

    public Coordinator(int port) {
        this.port = port;
        this.waitingCapacity = 0; // becomes 2 x total rooms as servers register
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Use: java Coordinator <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        new Coordinator(port).start();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Coordinator listening on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                // one thread per connection so clients and servers never block each other
                new Thread(() -> handleConnection(socket)).start();
            }
        } catch (IOException e) {
            System.out.println("Coordinator error: " + e.getMessage());
        }
    }

    // Reads messages from one connection (server or client) until it closes. A bad message is rejected but will not end connection
    private void handleConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    out.println(handleMessage(Message.parse(line)));
                } catch (IllegalArgumentException e) {
                    System.out.println("Rejected message: " + e.getMessage());
                    out.println(new Message("ERROR", "-", "Coordinator", e.getMessage()));
                }
            }
        } catch (IOException e) {
            System.out.println("Connection closed: " + e.getMessage());
        }
    }

    private Message handleMessage(Message m) {
        if (m.type.equals("REGISTER")) {
            registerServer(m);
            return new Message("ACKNOWLEDGMENT", m.requestId, "Coordinator", "registered");
        }
        else if (m.type.equals("ARRIVE")) {
            return handleArrive(m);
        }
        else if (m.type.equals("CAPACITY")) {
            updateCapacity(m);
            return new Message("ACKNOWLEDGMENT", m.requestId, "Coordinator", "capacity updated");
        }
        else {
            throw new IllegalArgumentException("unknown message type " + m.type);
        }
    }
    //updates the capacity of a server based on the received CAPACITY message
 private synchronized void updateCapacity(Message m) {
        ServerInfo info = servers.get(m.senderId);
        if (info == null) {
            throw new IllegalArgumentException("unknown server " + m.senderId);
        }
        int free = Integer.parseInt(m.field(0));
        info.freeRooms = free;
        System.out.println("Updated " + m.senderId + " capacity: " + free + "/" + info.totalRooms + " free");
    }
    private synchronized void registerServer(Message m) {
        String host = m.field(0);
        int serverPort = Integer.parseInt(m.field(1));
        int rooms = Integer.parseInt(m.field(2));
        if (rooms < 1) {
            throw new IllegalArgumentException("a server needs at least 1 room");
        }

        servers.put(m.senderId, new ServerInfo(m.senderId, host, serverPort, rooms));

        int totalRooms = 0;
        for (ServerInfo s : servers.values()) {
            totalRooms += s.totalRooms;
        }
        waitingCapacity = 2 * totalRooms;

        System.out.println(m.senderId + " registered at " + host + ":" + serverPort + " with " + rooms + " rooms");
        System.out.println("Waiting capacity = " + waitingCapacity);
    }

   private synchronized Message handleArrive(Message m) {
        System.out.println("Coordinator received ARRIVE from " + m.senderId);

        while (true) {
            ServerInfo bestServer = selectBestServer();
            if (bestServer == null) {
                break; // No servers with free rooms left to try
            }

            Message reserveReq = new Message("RESERVE_REQUEST", m.requestId, "Coordinator", m.senderId);
            Message reply = sendReserveRequest(bestServer, reserveReq);

            if (reply != null && "RESERVE_RESPONSE".equals(reply.type)) {
                int roomNumber = Integer.parseInt(reply.field(0));
                if (roomNumber != -1) {
                    System.out.println("Assigned " + m.senderId + " to " + bestServer.serverId + " room " + roomNumber);
                    return new Message("RESERVATION", m.requestId, "Coordinator", bestServer.host, String.valueOf(bestServer.port), String.valueOf(roomNumber));
                }
            }

            // Server returned -1 or failed to respond; mark it 0 so selectBestServer() tries another server
            bestServer.freeRooms = 0;
        }

        if (waitingQueue.size() >= waitingCapacity) {
            System.out.println("Queue full. Rejecting " + m.senderId);
            return new Message("REJECTED", m.requestId, "Coordinator", "system full");
        }

        // Message added to the back of queue
        waitingQueue.offer(m);
        System.out.println("Added " + m.senderId + " to waiting queue.");
        return new Message("WAITING", m.requestId, "Coordinator", "queued at position " + waitingQueue.size());
    }

    private synchronized ServerInfo selectBestServer() {
        ServerInfo best = null;
        for (ServerInfo s : servers.values()) {
            if (s.freeRooms > 0) {
                if (best == null || s.freeRooms > best.freeRooms) {
                    best = s;
                }
            }
        }
        return best;
    }

    private Message sendReserveRequest(ServerInfo server, Message request) {
        try (Socket socket = new Socket(server.host, server.port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(request);
            String responseLine = in.readLine();
            if (responseLine != null) {
                return Message.parse(responseLine);
            }
        } catch (IOException e) {
            System.out.println("Failed to reach server " + server.serverId + ": " + e.getMessage());
        }
        return null;
    }

    // First in queue retrieved and removed. Returns null if empty
    private synchronized Message getWaitingClient() {
        return waitingQueue.poll();
    }
}
