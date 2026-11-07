/********************************
Name: Amy, Brittany, Cole, Thomas
Problem Set: Final Group Project
Due Date: November 6, 2026
********************************/

package edu.uafs.fittingroom.protocol;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Message {
    public String type;
    public String requestId;
    public String senderId;
    public long timestamp;
    public List<String> fields = new ArrayList<>();

    public Message(String type, String requestId, String senderId, String... fields) {
        this.type = type;
        this.requestId = requestId;
        this.senderId = senderId;
        this.timestamp = System.currentTimeMillis();
        for (String f : fields) {
            this.fields.add(f);
        }
    }

    // Turns one line (TYPE|requestId|senderId|timestamp|field1|...) into a Message
    public static Message parse(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 4) {
            throw new IllegalArgumentException("message needs a type, request id, sender id, and timestamp");
        }

        Message m = new Message(parts[0], parts[1], parts[2]);
        try {
            m.timestamp = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("timestamp is not a number");
        }
        for (int i = 4; i < parts.length; i++) {
            m.fields.add(parts[i]);
        }
        return m;
    }

    public String field(int i) {
        if (i >= fields.size()) {
            throw new IllegalArgumentException(type + " is missing a field");
        }
        return fields.get(i);
    }

    // Turns Timestamp as a readable local time, e.g. 2026-11-06 20:16:27.076
    public String readableTime() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(timestamp));
    }

    // Displays text for printing to the console (toString stays the network format)
    public String displayMessage() {
        String text = type + " [" + requestId + "] from " + senderId + " at " + readableTime();
        if (!fields.isEmpty()) {
            text += " " + fields;
        }
        return text;
    }

    @Override
    public String toString() {
        String line = type + "|" + requestId + "|" + senderId + "|" + timestamp;
        for (String f : fields) {
            line += "|" + f;
        }
        return line;
    }
}