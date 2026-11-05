/********************************
Name: Amy, Brittney, Michael, Thomas
Problem Set: Final Group Project
Due Date: November 6, 2026
********************************/

package edu.uafs.fittingroom.server;

public class FittingRoomServer {
    private String serverId;
    private String coordHost;
    private int coordPort;
    private int listenPort;
    private int roomCount;

    public FittingRoomServer(String serverId, String coordHost, int coordPort, int listenPort, int roomCount) {
        this.serverId = serverId;
        this.coordHost = coordHost;
        this.coordPort = coordPort;
        this.listenPort = listenPort;
        this.roomCount = roomCount;
    }

    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Use: java FittingRoomServer <serverId> <coordHost> <coordPort> <listenPort> <rooms>");
            return;
        }

        String serverId = args[0];
        String coordHost = args[1];
        int coordPort = Integer.parseInt(args[2]);
        int listenPort = Integer.parseInt(args[3]);
        int roomCount = Integer.parseInt(args[4]);
        new FittingRoomServer(serverId, coordHost, coordPort, listenPort, roomCount).start();
    }
}