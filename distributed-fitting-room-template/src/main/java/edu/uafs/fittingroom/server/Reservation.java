package edu.uafs.fittingroom.server;

// What the server sends back when it reserves a room
public record Reservation(int roomId, String token, long expiresAt) { }