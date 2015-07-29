 /*
 * Copyright 2014 Health and Social Care Information Centre
 Solution Assurance damian.murphy@hscic.gov.uk

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License..
 */

package org.warlock.spine.connection;
import org.warlock.spine.logging.SpineToolsLogger;
import org.warlock.spine.messaging.DefaultFileSaveEbXmlHandler;
import org.warlock.spine.messaging.DefaultFileSaveSynchronousResponseHandler;
import org.warlock.spine.messaging.EbXmlMessage;
import org.warlock.spine.messaging.ExpiredMessageHandler;
// import org.warlock.spine.messaging.ITKTrunkHandler;
import org.warlock.spine.messaging.NullSynchronousResponseHandler;
import org.warlock.spine.messaging.Sendable;
import org.warlock.spine.messaging.SpineEbXmlHandler;
import org.warlock.spine.messaging.SynchronousResponseHandler;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.warlock.spine.messaging.acknowledgements.BusAckDistributionEnvelopeHandler;
import org.warlock.spine.messaging.acknowledgements.SendCDADistributionEnvelopeHandler;

/**
 * Singleton class to initialise and manage the resources used for sending and
 * receiving messages from TMS. Configured via System.properties.
 * 
 * @author Damian Murphy damian.murphy@hscic.gov.uk
 */
public class ConnectionManager {

    /**
     * System property. Name of the class to use as a password provider for the security context. If unset
     * this will default to the DefaultPropertyReadPasswordProvider class, which reads
     * the password from System properties and is <b>not recommended</b> for production
     * use.
     */
    private static final String PASSWORD_PROVIDER = "org.warlock.spine.connection.passwordproviderclass";
    
    /**
     * System property. Directory used for persisting reliable messages.
     */
    private static final String MESSAGE_DIRECTORY = "org.warlock.spine.messaging.messagedirectory";
    
    /**
     * System property. Directory used by the message expiry handler to save reliable messages that
     * have exceeded either their persistDuration or their retry counts without
     * getting an acknowledgment or an explicit error.
     */
    private static final String EXPIRED_DIRECTORY = "org.warlock.spine.messaging.expireddirectory";
    
    /**
     * System property. This is the period in milliseconds that the "retry process" is to 
     * run to check for reliable messaging retries, and also to handle expiry. Default value
     * is 30,000 (30 seconds).
     */
    private static final String RETRY_TIMER_PERIOD = "org.warlock.spine.messaging.retrytimerperiod";
    
    /**
     * System property. Name of a file containing persistDuration values, by service/interaction
     * type. This is used by the Listener process so that it has information on the persistDuration
     * value to use for an inbound reliable message (specifically, to scope de-duplication), 
     * without its having to consult SDS. If this is not set, the internal version is used which
     * can be found at org.warlock.spine.connection.persistDurations.txt.
     */
    private static final String PERSIST_DURATION_FILE = "org.warlock.spine.messaging.persistdurations";
    
    /**
     * REQUIRED System property. Value of local IP address to populate the "From" element of
     * a Spine SOAP message. This is provided as a system property to avoid the MHS having
     * to make an arbitrary guess on systems with multiple interfaces.
     */
    private static final String MY_IP = "org.warlock.spine.connection.myip";
    
    /**
     * REQUIRED System property. ASID for this MHS instance.
     * 
     * Note that as of the current version, multiple ASIDs for a single MHS instance
     * are not yet supported.
     */
    private static final String MY_ASID = "org.warlock.spine.sds.myasid";
    
    /**
     * REQUIRED System property. Party key for this MHS instance.
     * 
     * As of the current version, multiple party keys in a single MHS instance
     * are not yet supported.
     */
    private static final String MY_PARTYKEY = "org.warlock.spine.sds.mypartykey";
    
    /**
     * System property. If set this will declare a class implementing org.warlock.spine.connection.SessionCaptor
     * which will be used by the transmitter to record messages transmitted over the wire, and their synchronous
     * responses.
     */
    private static final String SESSION_CAPTOR = "org.warlock.spine.messaging.sessioncaptureclass";
    
    /**
     * System property. If this is set to something beginning with "y" or "Y", the 
     * Connection Manager will use the NullSynchronousResponseHandler as the the default
     * handler (i.e. used unless an explicit instance has been set for a request type)
     * for responses to synchronous requests.
     */
    private static final String USE_NULL_DEFAULT_SYNCHRONOUS_HANDLER = "org.warlock.spine.sds.nulldefaultsynchronoushandler";
    
    /**
     * Source identification for logging.
     */
    private static final String LOGSOURCE = "Spine ConnectionManager";
    private static final String TABSEPARATOR = "\t";
    private static final int HTTPS = 443;
    
    private static final String INTERNAL_PERSIST_DURATION_FILE = "persistDurations.txt";
    
    // Set this for now. Might want to make it a property in future.
    // Three - for send retry, receive expiry and SDS cache management
    //
    private static final int TIMER_THREAD_POOL_SIZE = 3;
    
    /**
     * Reference to an inbound connection listener, for asynchronous responses and
     * "unsolicited" messages.
     */
    private Listener listener = null;
    
    /**
     * Reference to the Spine Security Context that contains cryptographic details
     * and issues TLS connections.
     */
    private SpineSecurityContext securityContext = null;
        
    /**
     * This singleton instance.
     */
    private static final ConnectionManager me = new ConnectionManager();
    
    private SessionCaptor sessionCaptor = null;
    
    /**
     * Any exceptions thrown during the initialisation of the ConnectionManager
     * are reported on stderr, and a copy of the exception itself is stored here.
     * It can be retrieved using the getBootException() method.
     */
    private static Exception bootException = null;
    
    private String messageDirectory = null;
    private String expiredDirectory = null;
    private String myIp = null;
    private String myAsid = null;
    private String myPartyKey = null;

    private PasswordProvider passwordProvider = null;
    private SDSconnection sdsConnection = null;
    private ScheduledThreadPoolExecutor timer = null;
    
    private static final long DEFAULTRETRYCHECKINTERVAL = 30000;
    private long retryCheckPeriod = DEFAULTRETRYCHECKINTERVAL;
    
    /**
     * "Reliable" ebXML requests that have not yet been acknowledged, keyed on message id.
     */ 
    private HashMap<String, Sendable> requests = null;
     /**
     * "Handler" implementations for received Spine messages (in this version, these are all for received
     * asynchronous Spine responses, or other ebXML notifications). Keyed on SOAPaction derived from the
     * SDS "SVCIA" value.
     */ 
    private HashMap<String, SpineEbXmlHandler> handlers = null;

    /**
     * For any cases where a "reliable" ebXML message expires its persistDuration, a handler can be
     * provided to take care of any subsequent actions (beyond the default of just logging it, saving
     * the message, and stopping any more automatic retries). Keyed on the SOAPaction derived from the
     * SDS SVCIA value.
     */ 
    private HashMap<String, ExpiredMessageHandler> expiryHandlers = null;
    
    /*
     * To avoid having to do lookups on SDS (even in the cache) for inbound messages that we may not 
     * have seen before, this contains a pre-canned list of persistDuration time spans as Long, keyed on
     * the SOAPaction derived from the SDS SVCIA values.
     */ 
    private HashMap<String, Long> persistDurations = null;
    
    /**
     * For synchronous Spine requests, this is a handler keyed on the SOAPaction of the outbound request 
     */ 
    private HashMap<String, SynchronousResponseHandler> synchronousHandlers = null;
    
    
    /**
     * Default handler for synchronous responses where we've not been given anything specific
     */
    private SynchronousResponseHandler defaultSynchronousResponseHandler = null;
    
    /**
     * Default handler for an EbXML message, used when there isn't a specific handler set for
     * the service/interaction for the received message.
     */
    private DefaultFileSaveEbXmlHandler defaultEbXmlHandler = null;
    
    private SDSSpineEndpointResolver resolver = null;
    
    private static final String TKW_CLEAR = "tkw.spine-test.cleartext";

    /**
     * Singleton constructor.
     */ 
    @SuppressWarnings("UseSpecificCatch")
    private ConnectionManager()
    {
        /**
         * See if we're using clear-text sockets.
         */
        if (ConditionalCompilationControls.TESTHARNESS) {
            String ct = System.getProperty(TKW_CLEAR);
            ConditionalCompilationControls.cleartext = ((ct != null) && (ct.toLowerCase().startsWith("y")));
        }
        try {
            makePasswordProvider();
            securityContext = new SpineSecurityContext(passwordProvider);
        }
        catch (Exception e){
            bootException = e;
            return;
        }
        requests = new HashMap<>();
        expiryHandlers = new HashMap<>();
        handlers = new HashMap<>();
        synchronousHandlers = new HashMap<>();
        /**
         * Default ITK Trunk handler. This is instantiated automatically by the MHS because there is
         * no "Spine message" handling of an ITK trunk message besides returning an asynchronous ebXML
         * which thew MHS does anyway under the control of the contract properties. What it does need to
         * do is to extract the ITK Distribution Envelope, see what service it is for, and call an
         * ITK handler. The default behaviour can be overridden by explicitly setting a handler for 
         * the ITK Trunk service/interaction.
         */
        Exception e = null;

        if (ConditionalCompilationControls.ITK_TRUNK) {
            @SuppressWarnings("UnusedAssignment")
            org.warlock.spine.messaging.ITKTrunkHandler itkTrunkHandler = null;
            itkTrunkHandler = new org.warlock.spine.messaging.ITKTrunkHandler();
            handlers.put("urn:nhs:names:services:itk/COPC_IN000001GB01", itkTrunkHandler);
            handlers.put("\"urn:nhs:names:services:itk/COPC_IN000001GB01\"", itkTrunkHandler);
            try {
                // Add specific handler for ITK correspondence payloads in an ITK trunk message
                String p = System.getProperty("org.warlock.spine.correspondence.host");
                if (p != null && p.trim().toLowerCase().startsWith("y")) {
                    itkTrunkHandler.addHandler("urn:nhs-itk:services:201005:SendCDADocument-v2-0", new SendCDADistributionEnvelopeHandler());
                }
                p = System.getProperty("org.warlock.spine.correspondence.client");
                if (p != null && p.trim().toLowerCase().startsWith("y")) {
                    itkTrunkHandler.addHandler("urn:nhs-itk:services:201005:SendBusinessAck-v1-0", new BusAckDistributionEnvelopeHandler());
                }
            } catch (FileNotFoundException | IllegalArgumentException edefaulthandler) {
                e = edefaulthandler;
            }
        }
        try {
            String nullDefaultHandler = System.getProperty(USE_NULL_DEFAULT_SYNCHRONOUS_HANDLER);
            if ((nullDefaultHandler == null) || (!nullDefaultHandler.toLowerCase().startsWith("y"))) {
                defaultSynchronousResponseHandler = new DefaultFileSaveSynchronousResponseHandler();
            } else {
                defaultSynchronousResponseHandler = new NullSynchronousResponseHandler();
            }
            defaultEbXmlHandler = new DefaultFileSaveEbXmlHandler();
        } catch (FileNotFoundException | IllegalArgumentException edefaulthandler) {
            e = edefaulthandler;
        }

        messageDirectory = System.getProperty(MESSAGE_DIRECTORY);        
        if (messageDirectory == null)
            e = new Exception("Empty " + MESSAGE_DIRECTORY + " property");
        expiredDirectory = System.getProperty(EXPIRED_DIRECTORY);
        if (expiredDirectory == null)
            e = new Exception("Empty " + EXPIRED_DIRECTORY + " property");
        myIp = System.getProperty(MY_IP);
        if (myIp == null)
            e = new Exception("Empty " + MY_IP + " property");
        myAsid = System.getProperty(MY_ASID);
        if (myAsid == null)
            e = new Exception("Empty " + MY_ASID + " property");
        myPartyKey = System.getProperty(MY_PARTYKEY);
        if (myPartyKey == null)
            System.err.println("Empty " + MY_PARTYKEY + " property. This is not an error provided that the MHS installation isn expected only to handle Spine synchronous requests.");

        try {
            // Get the SDSSpineEndpointResolver to make the SDSconnection itself if it wants one...
            //
            resolver = new SDSSpineEndpointResolver();
        }
        catch (Exception esds) {
            e = esds;
        }
        String s = System.getProperty(RETRY_TIMER_PERIOD);
        try {
            retryCheckPeriod = Integer.parseInt(s);
        }
        catch (java.lang.NumberFormatException | java.lang.NullPointerException er) {}
        
        persistDurations = loadReceivedPersistDurations();
        
        String sc = System.getProperty(SESSION_CAPTOR);
        if (sc != null) {
            try {
                sessionCaptor = (SessionCaptor)((Class.forName(sc)).newInstance());
            }
            catch (Exception esc) {
                System.err.println("Error instantiating SessionCaptor " + sc + " : " + esc.toString());
            }
        }
    }
    
    SessionCaptor getSessionCaptor() { return sessionCaptor; }
    
    public String getMessageDirectory() { return messageDirectory; }
    
    /**
     * Called at start-up if the system needs to load any persisted, reliable
     * messages for sending. This applies the persist duration for the message
     * type to the declared timestamp and will run the expire() method on
     * anything that has expired whilst the MHS was down.
     * 
     * @throws Exception 
     */
    public void loadPersistedMessages()
            throws Exception
    {
        if (ConditionalCompilationControls.TESTHARNESS)
            return;
        File d = new File(messageDirectory);
        File[] messages = d.listFiles();
        EbXmlMessage ebxml = null;
        for (File f : messages) {
            try (FileInputStream fis = new FileInputStream(f)) {
                ebxml = new EbXmlMessage(fis);
            }
            catch (Exception e) {
                // TODO: Log failed attempt to get persisted message
                continue;
            }
            // See if we need to expire it
            //
            Calendar check = Calendar.getInstance();
            Calendar expiryTime = ebxml.getStarted();
            Long pd = persistDurations.get(ebxml.getHeader().getSvcIA());
            int p = 0;
            if (pd != null)
                p = pd.intValue();
            expiryTime.add(Calendar.SECOND, p);
            if (expiryTime.before(check)) {
                depersist(ebxml.getMessageId());
                ebxml.expire();
            } else {
                // Add it to requests
                requests.put(ebxml.getMessageId(), ebxml);
            }
        }
    }
    
    public void stopRetryProcessor()
    {
        if (timer == null)
            return;
        try {
            timer.shutdown();
        }
        catch (Exception e) {
            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.ConnectionManager.stopRetryProcessor", e);
        }
    }
    
    PasswordProvider getPasswordProvider() { return passwordProvider; }
    public String getMyIP() { return myIp; }
    public String getMyAsid() { return myAsid; }
    public String getMyPartyKey() { return myPartyKey; }
    public String getExpiredMessageDirectory() { return expiredDirectory; }
    public SpineSecurityContext getSecurityContext() { return securityContext; }
    
    /**
     * Resolves a list of SdsTransmissionDetails matching the given parameters. If there is
     * a local cache, this is read first and any match returned from there. Otherwise (or if
     * there is no match from the local cache), SDS is queried. Any details retrieved from
     * SDS are cached locally before being returned.
     * 
     * @param s Required service/interaction, matches SDS nhsSMhsSvcIA
     * @param o Required ODS code of the owning organisation matches SDS nhsIDcode.
     * @param a Optional ASID filter, or null to apply no filter.
     * @param p Optional party key filter, or null to apply no filter.
     * @return ArrayList &lt;SdsTransmissionDetails&gt; of matches. Note that in the case of multiple
     * matches this has no explicit order. In the case of no match, the list will be empty so an
     * isEmpty() check should be performed before trying to use the output of this method.
     */
    public ArrayList<SdsTransmissionDetails> getTransmissionDetails(String s, String o, String a, String p)
    {
        return resolver.getTransmissionDetails(s, o, a, p);
    }        
    
    /**
     * Get any exceptions reported from booting the Connection Manager.
     * @return Boot exception, or null if the Connection Manager was started successfully.
     */
    public Exception getBootException() { return bootException; }
    
    /**
     * Singleton getInstance().
     * @return A reference to the ConnectionManager instance.
     */
    public static ConnectionManager getInstance() 
    { 
        return me; 
    }
    
    /**
     * Starts the listener, and sets it listening, if it isn't already so. This method
     * will do nothing if the listener is already listening.
     * 
     * @param p Port on which to listen
     * @throws Exception If something goes wrong starting the listener.
     */
    public void listen(int p)
            throws Exception
    {
        if (listener == null) {
            listener = new Listener(p);
            listener.setPersistDurations(persistDurations);
        }
        if (!listener.isListening()) {
            listener.startListening(null);
        }
    }
    
    /**
     * Starts the listener, and sets it listening, if it isn't already so. This method
     * will do nothing if the listener is already listening. Uses port 443 by default.
     * 
     * @throws Exception If something goes wrong starting the listener.
     */
    public void listen()
            throws Exception
    {
        if (listener == null) {
            listener = new Listener();
            listener.setPersistDurations(persistDurations);
        }
        if (!listener.isListening()) {
            listener.startListening(null);
        }
    }
    
    /**
     * Stops the listener, listening.
     */
    public void stopListening()
    {
        if (listener == null)
            return;
        listener.stopListening();
       
        // Added to elimintate invalid thread state exceptions when restarting a listener
        // this ensures a new thread is constructed every time
        listener = null;
    }
    
    /**
     * Sends a Spine message. This is a wrapper round the Transmitter thread and registers the
     * message with the retry mechanism if the contract properties require. It will also, for
     * asynchronous messages, start the listener if it is not already running.
     * 
     * @param s Concrete instance of Sendable, encapsulating the message to send.
     * @param c SDS details of recipient
     * @throws Exception if there was a Connection Manager boot exception, or if starting any
     * required listener fails,
     */
    public void send(Sendable s, SdsTransmissionDetails c)
            throws Exception
    {
        // Note: check this here so getInstance() doesn't have to throw any
        // exception - that means that we don't have to catch them in the 
        // Transmitter, which can just log if anything goes wrong with its
        // own processing.
        //
        if (bootException != null)
            throw bootException;
        
        if (!c.isSynchronous()) {
            listen();
            if ((s.getType() != Sendable.ACK) && (c.getDuplicateElimination().contentEquals("always"))) {
                synchronized(LOGSOURCE) {
                    if (timer == null) {
                        timer = new ScheduledThreadPoolExecutor(TIMER_THREAD_POOL_SIZE);
                        RetryProcessor rp = new RetryProcessor();
                        timer.scheduleAtFixedRate(rp, retryCheckPeriod, retryCheckPeriod, TimeUnit.MILLISECONDS);                
                    }
                }
                if (!requests.containsKey(s.getMessageId())) {
                    requests.put(s.getMessageId(), s);
                }
            }
        }
        Transmitter t = new Transmitter(s);
        t.start();
    }
    
    /**
     * Worker method called by the retry processing timer. This checks any reliable
     * messages that have not yet had an acknowledgement (or explicit error) to see if they
     * need retrying, or if they are due for expiry. Any expired messages have the expiry
     * process, including any user-supplied expiry handler, called on them. 
     * 
     * It also requests that the listener
     * checks its de-duplication list against persist durations, and clears out any 
     * message ids from that list that are out of scope.
     */
    void processRetries()
    {
        if ((requests == null) || (requests.isEmpty())) {
            listener.cleanDeduplicationList();
            return;
        }
        Calendar check = Calendar.getInstance();
        ArrayList<Sendable> expires = new ArrayList<>();
        for (Sendable s : requests.values()) {
            Calendar expiryTime = s.getStarted();
            expiryTime.add(Calendar.SECOND, s.getPersistDuration());
            if (expiryTime.before(check)) {
                expires.add(s);
            } else {
                Calendar retryAfter = s.lastTry();
                if (retryAfter == null)
                    return;
                retryAfter.add(Calendar.SECOND, s.getRetryInterval());
                if (retryAfter.before(check)) {
                    (new Transmitter(s)).start();
                }
            }
        }
        for (Sendable s : expires) {
            try {
                removeRequest(s.getMessageId());
                s.expire();
            }
            catch (Exception e) {
                SpineToolsLogger.getInstance().log("org.warlock.spine.connection.ConnectionManager.expireException", e);
            }
        }
    }
    
    public int getPersistDuration(String svcia) {
        if (persistDurations.containsKey(svcia))
            return persistDurations.get(svcia).intValue();
        return -1;
    }
    /**
     * Returns a reference to the SDS connection. Whilst this method is not deprecated
     * as such, developers are recommended to use the supplied getTransmissionDetails()
     * method instead.
     * 
     * @return A reference to the SDS connection. 
     */
    public SDSconnection getSdsConnection() { return sdsConnection; }
    
    /**
     * Work around the design flaw in SDS where the nhsMhsEndpoint URL does not always
     * contain the URL that a sender needs to use. If there is a "resolved" URL for the
     * given service/interaction, then use it. Otherwise return null (in which case the 
     * message should be sent to the endpoint URL given in its SdsTransmissionDetails).
     * 
     * This is fed from the URL resolver file, which for testing purposes requires an
     * instance of the file per environment.
     * 
     * @param svcia Service-qualified interaction id for the message to be sent.
     * @return The URL to use, overriding the value from SDS itself, or null if the SDS value should be used. 
     */
    public String resolveUrl(String svcia)
    {
        if (resolver == null)
            return null;
        return resolver.resolveUrl(svcia);
    }
    
    private HashMap<String,Long> loadReceivedPersistDurations()
    {
        BufferedReader br = null;
        HashMap<String,Long> p = null;
        try {
            String s = System.getProperty(PERSIST_DURATION_FILE);
            if (s == null) {
                br = new BufferedReader(new InputStreamReader((org.warlock.spine.connection.ConnectionManager.class.getResourceAsStream(INTERNAL_PERSIST_DURATION_FILE))));
            } else {
                br = new BufferedReader(new FileReader(s));
            }
            String line = null;
            p = new HashMap<>();
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#"))
                    continue;
                if (line.trim().length() == 0)
                    continue;
                String[] fields = line.split("\t");
                String interaction = fields[0];
                int l = SdsTransmissionDetails.iso8601DurationToSeconds(fields[1]);
                p.put(interaction, new Long((long)l));
            }
            br.close();
        }
        catch (IOException e) {
            bootException  = e;
            return null;
        }
        return p;
    }
    
    private void makePasswordProvider()
            throws Exception
    {
        String pp = System.getProperty(PASSWORD_PROVIDER);
        if ((pp == null) || (pp.length() == 0))
        {
            passwordProvider = new DefaultPropertyReadPasswordProvider();
        } else {
            passwordProvider = (PasswordProvider)Class.forName(pp).newInstance();
        }
    }

    /**
     * Registers and instance of a SynchronousResponseHandler against the SOAP action of
     * the request. Note that this is a single instance, but will be used for all responses
     * to requests of the given type. Handler implementations must therefore be thread-
     * safe, re-entrant and reusable.
     * 
     * @param sa SOAP action of corresponding request type.
     * @param h Reference to handler implementation instance.
     */
    public void addSynchronousResponseHandler(String sa, SynchronousResponseHandler h)
    {
        synchronousHandlers.put(sa, h);
    }
    
    /**
     * Gets any SynchronousResponseHandler associated with the given SOAPaction. Returns a
     * reference to the default one if nothing explicit has been provided.
     * 
     * This is the principal integration point for Spine synchronous requests.
     * 
     * @param sa SOAP action of the original request.
     * @return A handler.
     */
    public SynchronousResponseHandler getSynchronousResponseHandler(String sa)
    {
        SynchronousResponseHandler h = synchronousHandlers.get(sa);
        if (h == null) 
            return defaultSynchronousResponseHandler;
        return h;
    }
    
    /**
     * Get a handler for a received ebXML message, keyed on SOAP action. This will
     * return any user-registered handler, or the default handler if no other is found. The
     * Connection Manager automatically registers a handler for ITK Trunk.
     * 
     * @param sa SOAP action for the received message
     * @return A handler.
     */
    public SpineEbXmlHandler getEbXmlHandler(String sa) 
    {
        SpineEbXmlHandler h = handlers.get(sa);
        if (h == null)
            return defaultEbXmlHandler;
        return h;
    }
    
    /**
     * Used for processing asynchronous acknowledgements. If the given message id is known
     * (i.e. if we have a request for it) then it is removed. Otherwise the unknown id is
     * logged. This is only called by the TMS listener at present, though in principle it
     * could be called for other transports.
     * 
     * @param a ebXml message id of the received acknowledgement or error notification
     */     
    public void registerAck(String a)
    {
        if (a == null)
            return;
        if (requests.containsKey(a)) {
            requests.remove(a);
            depersist(a);
        } else {
            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.ConnectionManager.registerAck", "Ack received for unrecognised message id");
        }
    }
    
    /**
     * Removes a reliable request from the retry list. Does nothing if null is passed
     * or if the message id is not known to the retry list.
     * 
     * @param a Message id of the request to remove.
     */
    void removeRequest(String a)
    {
        if (a == null)
            return;
        if (requests.containsKey(a)) {
            requests.remove(a);
            depersist(a);
        }        
    }
    
    private void depersist(String a) 
    {
        try {
            File f = new File(messageDirectory, a);
            if (f.exists())
                f.delete();
        }
        catch (Exception e) {
            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.ConnectionManager.depersistException", e);
        }
    }
    
    /**
     * Registers a handler for a Spine EbXML message, against the given SOAP action. Note
     * that a single instance of the handler is held in the Connection Manager, and will be
     * used for all received instances of that SOAP action. As such handlers MUST be thread-
     * safe, re-entrant and reusable.
     * 
     * This is the principal integration point for Spine ebXML messages.
     * 
     * @param sa SOAP action
     * @param h Handler instance.
     */
    public void addHandler(String sa, SpineEbXmlHandler h)
    {
        handlers.put(sa, h);
    }
    

    /**
     * Registers an expiry (i.e. called after a reliable message exceeds either its persist
     * duration or its maximum allowed retries, without being acknowledged or generating an
     * explicit error) handler against the given SOAP action. This provides behaviour
     * <i>in addition to</i> the normal expiry behaviour, which is to write a copy of the
     * expired message, to the expired message directory configured in system properties.
     * A single instance of any expiry handler is stored against the SOAP action, so any
     * user-supplied ExpiredMessageHandler implementations MUST be thread-safe, re-entrant and
     * reusable.
     * 
     * The principal reason to use this feature is to integrate with work flow processes that
     * need to be aware of failure to deliver a message.
     * 
     * @param sa SOAP action
     * @param ex The ExpiredMessageHandler implementation to call.
     */
    public void addExpiryHandler(String sa, ExpiredMessageHandler ex)
    {
        expiryHandlers.put(sa, ex);
    }
    
    /**
     * Get any ExpiredMessageHandler associated with the given SOAP action, or null if
     * none is found.
     * 
     * @param sa SOAP action
     * @return Handler, if present, or null.
     */
    public ExpiredMessageHandler getExpiryHandler(String sa)
    {
        if (!expiryHandlers.containsKey(sa))
            return null;
        return expiryHandlers.get(sa);
    }
    
    /**
     * A method to be used by any subclasses for substituting passed values into
     * a StringBuilder object using substitution tags
     *
     * @param sb StringBuilder representation of message to have substitutions
     * @param tag Substitution tags to be searched for and replaced
     * @param content Content which to substitute into the tags
     * @return Boolean response indicating if any substitutions have been made
     */
    public static boolean substitute(StringBuilder sb, String tag, String content) {
        boolean doneAnything = false;
        int tagPoint = -1;
        int tagLength = tag.length();
        if (content == null) {
            content = "";
        }
        while ((tagPoint = sb.indexOf(tag)) != -1) {
            sb.replace(tagPoint, tagPoint + tagLength, content);
            doneAnything = true;
        }
        return doneAnything;
    }    
}
