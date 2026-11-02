# distributed-fitting-room-template
Starter repository for the CS 30003 distributed fitting room final project.

# Distributed Fitting Room Simulator

CS 30003 — Distributed Systems  
Fall 2026 Final Group Project

## Project Description

This project simulates a retail fitting-room system using Java, threads,
semaphores, mutexes, TCP sockets, and multiple independent processes.

The system consists of:

- One central coordinator
- At least two fitting-room servers
- Multiple concurrent customer clients
- One global FIFO waiting area
- Automated concurrency and failure tests

## Required Features

- Thread-safe fitting-room allocation
- Global FIFO waiting queue
- Multiple fitting-room servers
- Socket-based communication
- Load-aware server selection
- Reservation tokens with expiration
- Server heartbeats
- Server-failure detection
- Client-disconnection recovery
- Duplicate-request protection
- Structured event logging
- Performance metrics
- Automated testing

## Repository Structure

## Repository Structure

```text
distributed-fitting-room-template/
│
├── .github/                              # GitHub automation and templates
│   ├── ISSUE_TEMPLATE/                   # Standard forms for creating issues
│   │   ├── bug-report.md                 # Reports defects and unexpected behavior
│   │   ├── sprint-task.md                # Defines a sprint development task
│   │   └── user-story.md                 # Describes a requirement as a user story
│   ├── workflows/                        # GitHub Actions automation
│   │   └── ci.yml                        # Automatically builds and tests the project
│   └── pull_request_template.md          # Pull-request format and checklist
│
├── docs/                                 # Project documentation
│   ├── sprint-reports/                   # Weekly Scrum and sprint reports
│   │   └── README.md                     # Instructions for sprint reports
│   ├── architecture.md                   # Architecture and component responsibilities
│   ├── message-protocol.md               # Network messages and timeout behavior
│   ├── testing-report.md                 # Test results, failures, and metrics
│   └── user-guide.md                     # Instructions for building and running
│
├── scripts/                              # System startup and testing scripts
│   └── README.md                         # Instructions for project scripts
│
├── src/                                  # Java source code and tests
│   ├── main/
│   │   ├── java/
│   │   │   └── edu/uafs/fittingroom/
│   │   │       ├── client/               # Customer clients and simulation
│   │   │       ├── common/               # Shared configuration and utilities
│   │   │       ├── coordinator/          # Coordinator and global waiting queue
│   │   │       ├── protocol/             # Messages and reservation tokens
│   │   │       └── server/               # Servers and room management
│   │   └── resources/                    # Application configuration files
│   │       └── README.md
│   │
│   └── test/
│       ├── java/
│       │   └── edu/uafs/fittingroom/
│       │       ├── client/               # Client unit tests
│       │       ├── coordinator/          # Coordinator unit tests
│       │       ├── integration/          # Socket and failure tests
│       │       ├── protocol/             # Protocol tests
│       │       └── server/               # Server unit tests
│       └── resources/                    # Test configuration and data
│           └── README.md
│
├── .gitignore                            # Files Git must ignore
├── CONTRIBUTING.md                       # Git and pull-request procedures
├── pom.xml                               # Maven build configuration
├── README.md                             # Project overview and instructions
└── TEAM.md                               # Team members and responsibilities
```
