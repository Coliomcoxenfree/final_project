/********************************
Name: Amy, Brittney, Michael, Thomas
Problem Set: Final Group Project
Due Date: November 6, 2026
********************************/

package edu.uafs.fittingroom.coordinator;

public class Coordinator {
    private int port;
    private int waitingCapacity; // 2 x total rooms

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
        System.out.println("Coordinator listening on port " + port);
    }
}