/********************************
Name: Amy, Brittany, Cole, Thomas
Problem Set: Final Group Project
Due Date: November 6, 2026
********************************/
 
package edu.uafs.fittingroom.coordinator;
 
public class ServerInfo {
    public String serverId;
    public String host;
    public int port;
    public int totalRooms;
    public int freeRooms;
 
    public ServerInfo(String serverId, String host, int port, int totalRooms) {
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.totalRooms = totalRooms;
        this.freeRooms = totalRooms;
    }
}