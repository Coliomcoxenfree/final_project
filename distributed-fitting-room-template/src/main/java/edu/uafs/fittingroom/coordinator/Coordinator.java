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

    //Now handles Capacity and heartbeat msgs
    private Message handleMessage(Message m) {
        if (m.type.equals("REGISTER")) {
            registerServer(m);
            return new Message("ACKNOWLEDGMENT", m.requestId, "Coordinator", "registered");
        }
        else if (m.type.equals("ARRIVE")) {
            return handleArrive(m);
        }
        else if(m.type.equals("CAPACITY_UPDATE")){
            updateCapacity(m);
             return new Message("ACKNOWLEDGMENT", m.requestId, "Coordinator", "capacity updated");
        }
        else if(m.type.equals("HEARTBEAT")){
            handleHeartbeat(m);
             return new Message("ACKNOWLEDGMENT", m.requestId, "Coordinator", "heartbeat recieved");
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
        
        //Testing if code works P1 - Can remove
        /*if(servers.size() >= 2){
            testSelectBestServer();
        }*/
    }

    private synchronized Message handleArrive(Message m) {
        System.out.println("Coordinator received ARRIVE from " + m.senderId);

        if (waitingQueue.size() >= waitingCapacity) {
            System.out.println("Queue full. Rejecting " + m.senderId);
            return new Message("REJECTED", m.requestId, "Coordinator", "system full");
        }
        // Message added to the back of queue
        waitingQueue.offer(m);
        System.out.println("Added " + m.senderId + " to waiting queue.");
        return new Message("WAITING", m.requestId, "Coordinator", "queued at position " + waitingQueue.size());
    }

    private synchronized void updateCapacity(Message m){
        ServerInfo server = servers.get(m.senderId);
        if(server == null){
            throw new IllegalArgumentException("Not registered: " + m.senderId);
        }
        int freeRs = Integer.parseInt(m.field(0));
        if(freeRs < 0 || freeRs > server.totalRooms){
            throw new IllegalArgumentException("Invalid room count");
        }

        server.freeRooms = freeRs;
        System.out.println(server.serverId + " capacity updated - " + server.freeRooms + " free rooms/" + server.totalRooms + " total rooms");
    }

    private synchronized void handleHeartbeat(Message m){
        ServerInfo server = servers.get(m.senderId);
        if(server == null){
            throw new IllegalArgumentException("Not registered: " + m.senderId);
        }
        updateCapacity(m);
        System.out.println("Heartbeat received from " + server.serverId);

    }
    
    private synchronized ServerInfo selectBestServer(){
        ServerInfo bestServer = null;
        for(ServerInfo server : servers.values()){
            if(server.freeRooms > 0){
                if(bestServer == null || server.freeRooms > bestServer.freeRooms || (server.freeRooms == bestServer.freeRooms && server.serverId.compareTo(bestServer.serverId) < 0)){
                    bestServer = server;
                }
            }
        }

        if(bestServer == null){
            System.out.println("Servers are all full");
            return null;
        }

        System.out.println("Selected server: " + bestServer.serverId + " has " + bestServer.freeRooms + " free rooms");
        return bestServer;
    }

    //Testing if code works P2 - Can remove
    /*private synchronized void testSelectBestServer(){
        ServerInfo s1 = servers.get("Server1");
        ServerInfo s2 = servers.get("Server2");

        //Save og 
        int og1 = s1.freeRooms;
        int og2 = s2.freeRooms;

        s1.freeRooms = 2;
        s2.freeRooms = 2;
        ServerInfo selected = selectBestServer();
        System.out.println("Selected: " + selected.serverId);

        s1.freeRooms = og1;
        s2.freeRooms = og2;
    }*/

    // First in queue retrieved and removed. Returns null if empty
    private synchronized Message getWaitingClient() {
        return waitingQueue.poll();
    }
}
