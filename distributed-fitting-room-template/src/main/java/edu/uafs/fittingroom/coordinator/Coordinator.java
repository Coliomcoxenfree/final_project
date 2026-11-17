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
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.HashSet;
import java.util.Set;

public class Coordinator {
    private int port;
    private int waitingCapacity; // 2 x total rooms
    private Map<String, ServerInfo> servers = new HashMap<>();
    private Queue<WaitingCustomer> waitingQueue = new LinkedList<>(); // global FIFO queue
    private Map<String, Message> completedRequests = new HashMap<>(); // completed request ids and message
    private Set<String> seenRequests = new HashSet<>(); // all request ids

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
                    Message reply = handleMessage(Message.parse(line), out);
                    if (reply != null) { // null means the handler already sent the reply itself
                        out.println(reply);
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("Rejected message: " + e.getMessage());
                    out.println(new Message("ERROR", "-", "Coordinator", e.getMessage()));
                }
            }
        } catch (IOException e) {
            System.out.println("Connection closed: " + e.getMessage());
        }
    }

    private synchronized Message handleMessage(Message m, PrintWriter out) {
        // check if requestid has been completed
        if (m.requestId != null ) {
            if (completedRequests.containsKey(m.requestId)) {
                System.out.println("[request=" + m.requestId + "] Duplicate " + m.type + " from " + m.senderId);
                return completedRequests.get(m.requestId);
            }
            // If seen and in queue this prevents from processing twice 
            if (!seenRequests.add(m.requestId)) {
                System.out.println("[request=" + m.requestId + "] Duplicate " + m.type + " from " + m.senderId + " in queue ignored");
                return null;
            }
        }
        try {
            Message response;
            if (m.type.equals("REGISTER")) {
                registerServer(m);
                response = new Message("ACKNOWLEDGMENT", m.requestId, "Coordinator", "registered");
            }
            else if (m.type.equals("ARRIVE")) {
                response = handleArrive(m, out);
            }
            else if (m.type.equals("DEPART")) {
                handleDepart(m);
                response = new Message("ACKNOWLEDGMENT", m.requestId, "Coordinator", "departed");
            }
            else if (m.type.equals("CAPACITY_UPDATE")) {
                updateCapacity(m);
                response = new Message("ACKNOWLEDGMENT", m.requestId, "Coordinator", "capacity updated");
            }
            else {
                throw new IllegalArgumentException("unknown message type " + m.type);
            }
            // save response for other duplicates
            if (m.requestId != null && response != null) {
                completedRequests.put(m.requestId, response);
            }
            return response;
        } catch (Exception e) {
            if (m.requestId != null) {
                seenRequests.remove(m.requestId);
            }
            System.out.println("Error: " + e.getMessage());
            return new Message("ERROR", "-", "coordinator", e.getMessage());
        }
    }

    // A customer leaves the system
    private synchronized void handleDepart(Message m) {
        String customerId = m.senderId;
        System.out.println("[request=" + m.requestId + "] " + customerId + " departed");
    }

    // Updates the capacity of a server based on the received CAPACITY_UPDATE message
    private synchronized void updateCapacity(Message m) {
        ServerInfo info = servers.get(m.senderId);
        if (info == null) {
            throw new IllegalArgumentException("unknown server " + m.senderId);
        }
        int free = Integer.parseInt(m.field(0));
        info.freeRooms = free;
        System.out.println("Updated " + m.senderId + " capacity: " + free + "/" + info.totalRooms + " free");
        serveWaitingCustomers();
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

    // A customer arrives. Order of preference: (1) a room right now, but only if nobody is already waiting, (2) a seat in the waiting queue, (3) rejection if the queue is full
    private synchronized Message handleArrive(Message m, PrintWriter out) {
        System.out.println("[request=" + m.requestId + "] Coordinator received ARRIVE from " + m.senderId);

        // people already waiting go first: give them any rooms that are free right now
        serveWaitingCustomers();

        if (waitingQueue.isEmpty()) {
            Message reservation = tryReserve(m.senderId, m.requestId);
            if (reservation != null) {
                return reservation;
            }
        }

        if (waitingQueue.size() >= waitingCapacity) {
            System.out.println("[request=" + m.requestId + "] Queue full. Rejecting " + m.senderId);
            return new Message("REJECTED", m.requestId, "Coordinator", "system full");
        }

        // Add the customer to the back of the waiting queue and immediately send them a WAITING message. Because this method is synchronized, the Coordinator holds its lock until the message is sent. This prevents another thread from sending a RESERVATION before the customer receives WAITING.
        waitingQueue.offer(new WaitingCustomer(m.senderId, m.requestId, out));
        System.out.println("[request=" + m.requestId + "] Added " + m.senderId + " to waiting queue.");
        out.println(new Message("WAITING", m.requestId, "Coordinator", "queued at position " + waitingQueue.size()));
        return null;
    }

    // Tries to get a room for one customer. Returns a RESERVATION message, or null if no server has room.
    private synchronized Message tryReserve(String customerId, String requestId) {
        while (true) {
            ServerInfo bestServer = selectBestServer();
            if (bestServer == null) {
                return null; // No servers with free rooms left to try
            }

            Message reserveReq = new Message("RESERVE_REQUEST", requestId, "Coordinator", customerId);
            Message reply = sendReserveRequest(bestServer, reserveReq);

            if (reply != null && "RESERVE_RESPONSE".equals(reply.type)) {
                int roomNumber = Integer.parseInt(reply.field(0));
                if (roomNumber != -1) {
                    bestServer.freeRooms--; 
                    System.out.println("[request=" + requestId + "] Assigned " + customerId + " to " + bestServer.serverId + " room " + roomNumber);
                    return new Message("RESERVATION", requestId, "Coordinator", bestServer.host, String.valueOf(bestServer.port), String.valueOf(roomNumber));
                }
            }

            // Server returned -1 or failed to respond; mark it 0 so selectBestServer() tries another server
            bestServer.freeRooms = 0;
        }
    }

    // Gives free rooms to waiting customers, oldest first. Stops at the first customer who cannot get a room, so nobody is ever served ahead of someone who arrived before them.
    private synchronized void serveWaitingCustomers() {
        while (!waitingQueue.isEmpty()) {
            WaitingCustomer next = waitingQueue.peek();
            Message reservation = tryReserve(next.customerId, next.requestId);
            if (reservation == null) {
                return; // no room yet; we try again on the next capacity update
            }
            waitingQueue.poll();
            next.out.println(reservation);
            completedRequests.put(next.requestId, reservation);
            System.out.println("[request=" + next.requestId + "] Served waiting customer " + next.customerId + " after " + (System.currentTimeMillis() - next.enqueuedAt) + " ms in the queue");
        }
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

            // Set a 2-second timeout so the program doesn't wait forever for a response from the server.
            socket.setSoTimeout(2000);
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
}