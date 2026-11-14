/********************************
Name: Amy, Brittany, Cole, Thomas
Problem Set: Final Group Project
Due Date: December 11, 2026
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
            if (reply == null) {
                return;
            }
           Message msg = Message.parse(reply);
            System.out.println(customerId + " received reply: " + msg.displayMessage());

            if ("RESERVATION".equals(msg.type)) {
                String serverHost = msg.field(0);
                int serverPort = Integer.parseInt(msg.field(1));
                String roomNumber = msg.field(2);

                // Wait a random 0-1 seconds
                Thread.sleep((long) (Math.random() * 1000));

                // Connect straight to the server and send RELEASE
                try (Socket serverSocket = new Socket(serverHost, serverPort);
                     PrintWriter serverOut = new PrintWriter(serverSocket.getOutputStream(), true);
                     BufferedReader serverIn = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()))) {

                    String releaseReqId = customerId + "-" + (++requestCount);
                    serverOut.println(new Message("RELEASE", releaseReqId, customerId, customerId, roomNumber));

                    String serverReply = serverIn.readLine();
                    if (serverReply != null) {
                        System.out.println(customerId + " received server reply: " + Message.parse(serverReply).displayMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.out.println(customerId + " could not connect: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
