# Architecture

Architecture diagram, process responsibilities, synchronization strategy, timeout values, and load-balancing strategy.

## Load-balancing strategy

When a customer arrives, the Coordinator selects the registered server that reports
the most free rooms (using the latest CAPACITY message from each server). It sends
that server a RESERVE_REQUEST. The server makes the final decision: it allocates a
room and replies with the room number, or replies -1 if it is actually full. On -1
the Coordinator treats that server as full and tries the next-best server. If no
server has a free room, the customer joins the global FIFO waiting queue; if the queue
is full (2 x total rooms), the customer receives REJECTED.

## Synchronization strategy

Each process protects its shared state with Java `synchronized` methods (monitor
locks). In a fitting room server, allocateRoom, releaseRoom and freeRoomCount share
one lock over the rooms. In the Coordinator, the waiting queue, server list and
waiting capacity share one lock. The two processes share no memory; they exchange
only messages.