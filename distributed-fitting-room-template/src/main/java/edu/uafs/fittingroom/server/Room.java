/********************************
Name: Amy, Brittany, Cole, Thomas
Problem Set: Final Group Project
Due Date: December 11, 2026
********************************/
 
package edu.uafs.fittingroom.server;
 
public class Room {
    public enum RoomState { FREE, OCCUPIED } // RESERVED will be added later
 
    public int roomId;
    public RoomState state = RoomState.FREE;
    public String ownerId = null; // the customer using this room
 
    public Room(int roomId) {
        this.roomId = roomId;
    }
}