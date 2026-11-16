/********************************
Name: Amy, Brittany, Cole, Thomas
Problem Set: Final Group Project
Due Date: December 11, 2026
********************************/

package edu.uafs.fittingroom.coordinator;

import java.io.PrintWriter;

// A customer sitting in the Coordinator's waiting queue
// We keep the customer's connection so the Coordinator can send the reservation later, once a room frees up, and the time they joined 
public class WaitingCustomer {
    public String customerId;
    public String requestId;     // the ARRIVE request this customer is waiting on
    public PrintWriter out;      // the customer's open connection
    public long enqueuedAt;      

    public WaitingCustomer(String customerId, String requestId, PrintWriter out) {
        this.customerId = customerId;
        this.requestId = requestId;
        this.out = out;
        this.enqueuedAt = System.currentTimeMillis();
    }
}