package edu.uafs.fittingroom.protocol;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.File;
import edu.uafs.fittingroom.protocol.Logger;

public class Performance {
    private long startTime;
    private long endTime;
    private long totalTime;
    private int totalRequests;
    private int totalCompletedRequests;
    private int totalFailedRequests;
    private PrintWriter fWriter;
    private Logger performanceLogger;

    public Performance(String processId, String fileName) {
        this.startTime = System.currentTimeMillis();
        this.totalRequests = 0;
        this.totalCompletedRequests = 0;
        this.totalFailedRequests = 0;
        this.performanceLogger = new Logger("Performance", "performance.log");
    }
    public synchronized void RequestsInc(boolean isSuccess) {
        totalRequests++;
        if (isSuccess == true) {
            totalCompletedRequests++;
        } else {
            totalFailedRequests++;
        }
    }

    public synchronized void endPerformance() {
        this.endTime = System.currentTimeMillis();
        this.totalTime = endTime - startTime;
        performanceLogger.log("PERFORMANCE_SUMMARY", "", "", "", "Total Requests: " + totalRequests + ", Completed: " + totalCompletedRequests + ", Failed: " + totalFailedRequests + ", Total Time (ms): " + totalTime);
    }
    
    public synchronized void logPerformance(String metricName, String metricValue) {
        performanceLogger.log(metricName + "PERFORMANCE_METRIC: ", "", "", "", metricName + " = " + metricValue);
    }
    //Closes log file
    public synchronized void close(){
        performanceLogger.close();
    }
}