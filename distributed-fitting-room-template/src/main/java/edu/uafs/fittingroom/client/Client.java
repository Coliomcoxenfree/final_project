/********************************
Name: Amy, Brittney, Michael, Thomas
Problem Set: Final Group Project
Due Date: November 6, 2026
********************************/

package edu.uafs.fittingroom.client;

public class Client extends Thread {
    private String customerId;
    private String coordHost;
    private int coordPort;

    public Client(String customerId, String coordHost, int coordPort) {
        this.customerId = customerId;
        this.coordHost = coordHost;
        this.coordPort = coordPort;
    }

    @Override
    public void run() {
        System.out.println(customerId + " starting");
        // connect to the Coordinator and send ARRIVE
    }
}