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

public class Coordinator {
    private int port;
    private int waitingCapacity; // 2 x total rooms
    private Map<String, ServerInfo> servers = new HashMap<>();

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
            System.out.println("Coordinator received ARRIVE from " + m.senderId);
            return new Message("ACKNOWLEDGMENT", m.requestId, "Coordinator", "received");
        }
        else {
            throw new IllegalArgumentException("unknown message type " + m.type);
        }
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
}
