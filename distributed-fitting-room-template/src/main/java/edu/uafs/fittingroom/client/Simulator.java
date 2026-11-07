/********************************
Name: Amy, Brittany, Cole, Thomas
Problem Set: Final Group Project
Due Date: November 6, 2026
********************************/

package edu.uafs.fittingroom.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Simulator {
    public static void main(String[] args) throws InterruptedException {
        if (args.length < 3) {
            System.out.println("Use: java Simulator <coordHost> <coordPort> <customers>");
            return;
        }

        String coordHost = args[0];
        int coordPort = Integer.parseInt(args[1]);
        int customers = Integer.parseInt(args[2]);

        System.out.println("Simulator creating " + customers + " customers");
        Random random = new Random();
        List<Client> clients = new ArrayList<>();

        for (int i = 1; i <= customers; i++) {
            Client c = new Client("C" + i, coordHost, coordPort);
            clients.add(c);
            c.start();
            Thread.sleep(random.nextInt(1000)); // random 0-1 s between arrivals
        }

        // wait for every customer thread to finish
        for (Client c : clients) {
            c.join();
        }
        System.out.println("Simulator done");
    }
}