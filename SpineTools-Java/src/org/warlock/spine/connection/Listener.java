/*

 Copyright 2014 Health and Social Care Information Centre
 Solution Assurance damian.murphy@hscic.gov.uk

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.warlock.spine.connection;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
//import javax.net.ServerSocketFactory;
//import javax.net.ssl.SSLServerSocket;
//import javax.net.ssl.SSLSocket;
import org.warlock.spine.logging.SpineToolsLogger;
import org.warlock.spine.messaging.EbXmlMessage;

/**
 * Listen for inbound Spine messages. Handle de-duplication for reliable
 * interactions, and call message handlers.
 *
 * This is typically started from within the ConnectionManager, using the
 * listen() method.
 *
 * @author Damian Murphy damian.murphy@hscic.gov.uk
 */
public class Listener
        extends Thread {

    private static final String LOGSOURCE = "Spine connection listener";

    /**
     * De-duplication list. Holds receipt times against message ids.
     */
    private HashMap<String, Calendar> receivedIds = null;

    private SocketAddress listenAddress = null;
    //private SSLServerSocket server = null;
    private ServerSocket server = null;
    private SpineSecurityContext tlsContext = null;
    private boolean listening = false;

    private final Object requestLock = new Object();
    private boolean cleaning = false;
    private HashMap<String, Long> persistDurations = null;
    private int listenPort = 4430;

    /**
     * Instantiate a listener using the default security context from the Connection
     * Manager.
     * 
     * @throws Exception 
     * Instantiate a listener using the default security context from the
     * Connection Manager.
     *
     * @throws Exception
     */
    Listener()
            throws Exception {
        init();
    }

    Listener(int p)
            throws Exception {
        listenPort = p;
        init();
    }

    /**
     * Internal call used by the Connection Manager to tell the listener about
     * the persist durations it has loaded.
     *
     * @param p HashMap of service/interaction values against persist duration
     * in seconds.
     */
    void setPersistDurations(HashMap<String, Long> p) {
        persistDurations = p;
    }

    /**
     * Create the listener based on the given <code>SpineSecurityContext</code>
     *
     * @param c SpineSecurityContext
     * @throws IllegalArgumentException if given a null context, or one that is
     * not set up.
     */
    public Listener(SpineSecurityContext c)
            throws IllegalArgumentException {
        if (c == null) {
            throw new IllegalArgumentException("Null context");
        }
        if (!c.isReady()) {
            throw new IllegalArgumentException("Context not ready");
        }
        tlsContext = c;
    }

    public void setPort(int p) {
        listenPort = p;
    }

    public int getPort() {
        return listenPort;
    }

    private void init()
            throws Exception {
        tlsContext = ConnectionManager.getInstance().getSecurityContext();
        receivedIds = new HashMap<>();
    }

    public boolean isListening() {
        return listening;
    }

    /**
     * Internal call from the ConnectionManager's processRetries() method, to
     * remove any message ids from the de-duplication list if they've been there
     * longer than their persistDuration.
     */
    void cleanDeduplicationList() {
        if (cleaning) {
            return;
        }
        ArrayList<String> expiredIds = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        cleaning = true;
        for (String s : receivedIds.keySet()) {
            Calendar exp = receivedIds.get(s);
            if (now.compareTo(exp) > 0) {
                expiredIds.add(s);
            }
        }
        for (String s : expiredIds) {
            try {
                synchronized (requestLock) {
                    receivedIds.remove(s);
                }
            } catch (Exception e) {
            }
        }
        cleaning = false;
    }

    /**
     * Internal call from the Connection Manager to register the receipt of a
     * message. This method provides the de-duplication check, as well as
     * recording the receipt of a previously-unseen message.
     *
     * @param s The received message
     * @return true if this message is a duplicate, false otherwise.
     */
    boolean receiveId(EbXmlMessage s) {
        if (persistDurations == null) {
            return false;
        }
        String id = s.getMessageId();
        synchronized (requestLock) {
            if (receivedIds.containsKey(id)) {
                return true;
            }
            long l = 0;
            try {
                l = persistDurations.get(s.getHeader().getSvcIA());
            } catch (Exception e) {
                l = 3600;
            }
            Calendar c = Calendar.getInstance();
            c.add(Calendar.SECOND, (int) l);
            receivedIds.put(id, c);
            return false;
        }
    }

    /**
     * Stop listening and close the server socket.
     */
    public void stopListening() {
        if (!listening) {
            return;
        }
        listening = false;
        if (server != null) {
            try {
                server.close();
            } catch (IOException e) {
                // TODO: Log properly
                e.printStackTrace();
            }
        }
    }

    /**
     * Start listening on the given address, or on 0.0.0.0 if null
     *
     * @param a The address on which to liste, or 0.0.0.0 (all interfaces) if
     * null.
     * @throws Exception
     */
    public void startListening(SocketAddress a)
            throws Exception {
        // Avoid a race condition if there is an attempt to start the listener more than
        // once, before it has actually finished starting to listen.
        //
        if (this.getState() != Thread.State.NEW)
            return;
        if (listening)
            return;
        if (a == null) {
            listenAddress = new java.net.InetSocketAddress(listenPort);
        } else {
            listenAddress = a;
        }
        start();
        while (!listening) {
            sleep(1000);
        }
    }

    @Override
    public void run() {
        if (listening)
            return;
    this.setName("Listener");
    try {
            //server = (SSLServerSocket)tlsContext.getServerSocketFactory().createServerSocket();
            server = tlsContext.getServerSocketFactory().createServerSocket();
            server.bind(listenAddress);
        } catch (IOException e) {
            System.err.println("Binding...");
            e.printStackTrace(System.err);
            return;
        }
        listening = true;
        try {
            while (listening) {
                try {
                    //SSLSocket s = (SSLSocket)server.accept();
                    Socket s = server.accept();
                    (new SpineMessageHandler(this, s)).start();
                } catch (java.net.SocketException eSocket) {
                    if (!listening) {
                        System.out.println("Shutting down on command");
                        // exiting from run terminates the thread
                        return;
                    }
                    throw new Exception("SocketException in accept() loop: " + eSocket.toString());
                }
            }
        } catch (Exception e) {
            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.Listener.listenLoop", e);
        }

    } // run

    private void substitute(StringBuilder sb, String t, String o)
            throws Exception {
        int tagPoint = -1;
        int tagLength = t.length();
        while ((tagPoint = sb.indexOf(t)) != -1) {
            sb.replace(tagPoint, tagPoint + tagLength, o);
        }
    }

}
