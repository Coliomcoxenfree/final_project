/********************************
Name: Amy, Brittany, Cole, Thomas
Problem Set: Final Group Project
Due Date: November 6, 2026
********************************/

package edu.uafs.fittingroom.client;

import edu.uafs.fittingroom.protocol.Message;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client extends Thread {
    private String customerId;
    private String coordHost;
    private int coordPort;
    private int requestCount = 0;

    public Client(String customerId, String coordHost, int coordPort) {
        this.customerId = customerId;
        this.coordHost = coordHost;
        this.coordPort = coordPort;
    }

    @Override
    public void run() {
        // a problem with one client's socket only ends this thread, not the other clients
        try (Socket socket = new Socket(coordHost, coordPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String requestId = customerId + "-" + (++requestCount);
            out.println(new Message("ARRIVE", requestId, customerId, customerId));
            System.out.println("[request=" + requestId + "] Customer " + customerId + " enters the system");
            String reply = in.readLine();
            System.out.println(customerId + " received reply: " + Message.parse(reply).displayMessage());
            // will make code to where the reply becomes a reservation, a queued notification, or full
        } catch (IOException e) {
            System.out.println(customerId + " could not connect: " + e.getMessage());
        }
    }
}