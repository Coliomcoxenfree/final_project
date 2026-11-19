/********************************
Name: Amy, Brittany, Cole, Thomas
Problem Set: Final Group Project
Due Date: December 11, 2026
********************************/

package edu.uafs.fittingroom.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import edu.uafs.fittingroom.protocol.Logger;

public class Simulator {
    public static void main(String[] args) throws InterruptedException {
        if (args.length < 3) {
            System.out.println("Use: java Simulator <coordHost> <coordPort> <customers>");
            return;
        }

        String coordHost = args[0];
        int coordPort = Integer.parseInt(args[1]);
        int customers = Integer.parseInt(args[2]);
        //Logger for stim and client threads
        Logger logger = new Logger("Simulator", "simulator.log");
        logger.log("SIMULATION_STAERTED", "-", "-", "-", "Simulator creating " + customers + " customers");

        Random random = new Random();
        List<Client> clients = new ArrayList<>();

        for (int i = 1; i <= customers; i++) {
            Client c = new Client("C" + i, coordHost, coordPort, logger);
            clients.add(c);
            logger.log("CUSTOMER_CREATED", "C" + i, "-", "-", "Created customer C" + i);
            c.start();
            Thread.sleep(random.nextInt(1000)); // random 0-1 s between arrivals
        }

        // wait for every customer thread to finish
        for (Client c : clients) {
            c.join();
        }
        logger.log("SIMULATOR_COMPLETED", "-", "-", "-", "Simulator done");
        logger.close();
    }
}