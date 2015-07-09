/*
 * Logger.java
 *
 * Created on 13 April 2006, 10:27
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 * 14/06/2006 BIYA add comments
 */

package org.warlock.util;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;

/**
 *  A Singleton class for logging.
 *
 *  The logfile name pattern: [application name]_yyyyMMddHHmmss.log in the specified directory
 *
 *  Note:  setAppName(String name, String ldir) should be called before any loging call.  
 *         closeLog() will close the logging file.
 *         setAppName() and closeLog() should be called as a pair with a logging file.
 *
 * @author DAMU2
 */
public class Logger {
    
    private static final SimpleDateFormat dateStringFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    
    private static String logDir = null;
    private static String appName = null;
    private static String dateString = null;
    private static Logger me = null;
    private static BufferedWriter log = null;
    private static String logFileName = null;
    
    /** Creates a new instance of Logger */
    private Logger() {
        dateString = dateStringFormat.format(new Date());
    }
    
    /** open the logginf file */
    private void openFile() {
        StringBuilder sb = new StringBuilder(logDir);
        try {
            sb.append(System.getProperty("file.separator"));
            sb.append(appName);
            sb.append("_");
            sb.append(dateString);
            sb.append(".log");
            logFileName = sb.toString();
            log = new BufferedWriter(new FileWriter(logFileName));
        }
        catch (Exception e) {
            System.err.println("Failed to create log file: " + sb.toString() + "\n Reason: " + e.getMessage() + "\n");
        }
    }
    
    /**
     *  close the logging file.
     */
    public synchronized void closeLog() {
        if (log != null) { 
            try {
                log.flush();
            }
            catch (Exception e) {}
            log = null;
        }
    }
    
    public synchronized String dumpLog() {
        StringBuilder sb = new StringBuilder();
        String line = null;
        try {
            BufferedReader br = new BufferedReader(new FileReader(logFileName));
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append("\r\n");
            }
        }
        catch (Exception e) {
            return "Exception reading log: " + e.getMessage();
        }
        return sb.toString();
    }

    public static String getDate() { return dateStringFormat.format(new Date()); }
    /**
     *  logging.
     * 
     *  the pattern of record:[yyyyMMddHHmmss]: Location: [location] : Message: [message]
     *
     *  If the log file has not been opened successfully, the error will be sent via the standard err channel.
     *   
     *  @param location  where the message has been raised.
     *  @param message   the message to be logged. 
     */
    public synchronized void log(String location, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(dateStringFormat.format(new Date()));
        sb.append(": Location: ");
        if ((location == null) || (location.trim().length() == 0))
            sb.append("Not given");
        else
            sb.append(location);
        sb.append(" : Message: ");
        if ((message == null) || (message.trim().length() == 0))
            sb.append("Not given");
        else
            sb.append(message);
        if (log == null) {
            System.err.println("Attempt to write log failed, no logfile.\n");
            System.err.println(sb.toString());
            return;
        }
        try {
            log.write(sb.toString());
            log.write("\n");
            log.flush();
        }
        catch (Exception e) {
            System.err.println("Attempt to write log failed, exception on write: " + e.getMessage() + "\n");
            System.err.println(sb.toString());            
        }
    }
    
    /**
     *  return the singleton instance of this class.
     */
    public static synchronized Logger getInstance() {
        if (me == null)
            me = new Logger();
        return me;
    }
    
    /**
     *  set the appcation name and logging direction for the composition of 
     *  log filename 
     *  
     *  the function will attempt to create a log file;
     *  if succesful, the instance is ready for logging, 
     *  otherwise an error will be sent via the standard err channel.  
     *
     *  @param  name    application name
     *  @param  ldir    the log file directory
     */
    public void setAppName(String name, String ldir) {
        if ((name == null) || (name.trim().length() == 0))
            return;
        if ((ldir == null) || (ldir.trim().length() == 0))
            return;
        if (appName == null) {
            logDir = ldir;
            appName = name;
            openFile();
        }
    }

    public void log(String string) {
        this.log("Location not given", string);
    }
    
}
