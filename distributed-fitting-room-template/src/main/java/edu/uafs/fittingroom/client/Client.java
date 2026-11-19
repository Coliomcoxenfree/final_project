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
import edu.uafs.fittingroom.protocol.Logger;

public class Client extends Thread {
    private String customerId;
    private String coordHost;
    private int coordPort;
    private int requestCount = 0;
    private Logger logger;

    public Client(String customerId, String coordHost, int coordPort, Logger logger) {
        this.customerId = customerId;
        this.coordHost = coordHost;
        this.coordPort = coordPort;
        this.logger = logger;
    }

    @Override
    public void run() {
        logger.log("CUSTOMER_STARTED", customerId, "-", "-", customerId + " started");
        // a problem with one client's socket only ends this thread, not the other clients
        try (Socket socket = new Socket(coordHost, coordPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String requestId = customerId + "-" + (++requestCount);
            out.println(new Message("ARRIVE", requestId, customerId));
            logger.log("CUSTOMER_ARRIVED", customerId, "-", requestId, "Customer " + customerId + " enters the system");
            String reply = in.readLine();
            if (reply == null) {
                return;
            }
            Message msg = Message.parse(reply);
            System.out.println(customerId + " received reply: " + msg.displayMessage());

            if("WAITING".equals(msg.type)){
                logger.log("CUSTOMER_WAITING", customerId, "-", requestId, "Customer " + customerId + " is waiting for a room");
            }
            boolean wasWaiting = "WAITING".equals(msg.type);

            // WAITING means the Coordinator queued us. Keep listening until it offers a room.
            while ("WAITING".equals(msg.type)) {
                reply = in.readLine();
                if (reply == null) {
                    logger.log("WAITING_CONNECTION_LOST", customerId, "-", requestId, customerId + " lost connection while waiting");
                    System.out.println(customerId + " lost connection while waiting");
                    return;
                }
                msg = Message.parse(reply);
            
                System.out.println(customerId + " received reply: " + msg.displayMessage());
            }

            if("REJECTED".equals(msg.type)){
                logger.log("CUSTOMER_REJECTED", customerId, "-", requestId, customerId + " was rejected: " + msg.field(0));
            }

            if ("RESERVATION".equals(msg.type)) {
                if(wasWaiting){
                    logger.log("CUSTOMER_STOPPED_WAITING", customerId, "-",  requestId, "Customer " + customerId + " leaves the waiting queue");
                }
                String serverHost = msg.field(0);
                int serverPort = Integer.parseInt(msg.field(1));
                String roomNumber = msg.field(2);
                String token = msg.field(3);

                logger.log("RESERVATION_RECEIVED", customerId, "-", requestId, customerId + " received room " + roomNumber + " at " + serverHost + ":" + serverPort);

                // Connect straight to the server, claim the room with the token, then release it
                try (Socket serverSocket = new Socket(serverHost, serverPort);
                     PrintWriter serverOut = new PrintWriter(serverSocket.getOutputStream(), true);
                     BufferedReader serverIn = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()))) {
 
                    String claimReqId = customerId + "-" + (++requestCount);
                    serverOut.println(new Message("CLAIM", claimReqId, customerId, roomNumber, token));
                    String claimLine = serverIn.readLine();
                    Message claimReply = claimLine == null ? null : Message.parse(claimLine);
                    if (claimReply != null) {
                        System.out.println(customerId + " received server reply: " + claimReply.displayMessage());
                    }
 
                    if (claimReply != null && "ACKNOWLEDGMENT".equals(claimReply.type)) {
                        logger.log("ROOM_CLAIMED", customerId, "-", claimReqId, customerId + " successfully claimed room " + roomNumber);
                        // Wait a random 0-1 seconds in room
                        Thread.sleep((long) (Math.random() * 1000));
 
                        String releaseReqId = customerId + "-" + (++requestCount);
                        serverOut.println(new Message("RELEASE", releaseReqId, customerId, roomNumber));
                        String serverReply = serverIn.readLine();
                        if (serverReply != null) {
                            Message releaseReply = Message.parse(serverReply);
                            System.out.println(customerId + " received server reply: " + Message.parse(serverReply).displayMessage());
                            if("ACKNOWLEDGMENT".equals(releaseReply.type)){
                                logger.log("ROOM_RELEASED", customerId, "-", releaseReqId, customerId + " released room " + roomNumber);
                            }
                        }
                    } else {
                        logger.log("ROOM_CLAIM_FAILED", customerId, "-", claimReqId, customerId + " could not claim room " + roomNumber + " (rservation expired)");
                    }
                }

                // Leave the system: tell the Coordinator client is gone
                String departReqId = customerId + "-" + (++requestCount);
                out.println(new Message("DEPART", departReqId, customerId));
                String departReply = in.readLine();
                if (departReply != null) {
                    System.out.println(customerId + " received reply: " + Message.parse(departReply).displayMessage());
                }
            }
        } catch (IOException e) {
           logger.log("CLIENT_CONNECTION_ERROR", customerId, "-", "-", customerId + " could not connect: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
