package edu.uafs.fittingroom.protocol;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.File;

public class Logger {
    private String processId;
    private PrintWriter fWriter;
    
    //Creates log file
    public Logger(String processId, String fileName){
        this.processId = processId;
        File folder = new File("Logs");
        if(!folder.exists()){
            folder.mkdirs();
        }

        try{
            fWriter = new PrintWriter(new FileWriter(new File(folder, fileName), true));
        }
        catch(IOException e){
            System.out.println("Can't create the log file: " + e.getMessage());
        }
    
    }

    //Logs an event
    public synchronized void log(String eventType, String customerId, String serverId, String requestId, String consoleMsg){
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
        String time = now.format(format);
        System.out.println(consoleMsg);

        String logMsg = time + " | " + processId + " | " + eventType + " | cust=" + customerId + " | server=" + serverId + " | req=" + requestId; 
        if(fWriter != null){
            fWriter.println(logMsg);
            fWriter.flush();
        }
    }

    //Closes log file
    public synchronized void close(){
        if(fWriter != null){
            fWriter.close();
        }
    }
    
}

