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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.Socket;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.output.TeeOutputStream;
//import javax.net.ssl.SSLSocket;
//import javax.net.ssl.SSLSocketFactory;
import org.warlock.spine.logging.SpineToolsLogger;
import org.warlock.spine.messaging.Sendable;
import org.warlock.spine.messaging.SpineSOAPRequest;
import org.warlock.spine.messaging.SynchronousResponseHandler;

/**
 * Thread to handle sending messages. The run() method of this class opens a TLS
 * connection to Spine, and gets the Sendable message to serialise itself to the
 * connection's OutputStream. It then waits for an HTTP response.<BR><BR>
 *
 * For a synchronous request the appropriate SynchronousResponseHandler is
 * called, note that, for this case, the Transmitter does NOT handle HTTP 500
 * responses, nor does it make any distinction between the various possible HL7
 * "success" and "fail" messages that can be returned - these are left either to
 * the handler, or to the class that constructed the synchronous request and
 * asked the ConnectionManager to send it. The full HTTP response body is stored
 * in the Sendable.synchronousResponse member, as a String.<BR><BR>
 *
 * For an asynchronous request, the Transmitter handles synchronous acks or
 * errors signalling termination of retries.<BR><BR>
 *
 * @author Damian Murphy damian.murphy@hscic.gov.uk
 */
public class Transmitter
        extends java.lang.Thread {

    private static final String PROXYHOST = "org.warlock.spine.proxyhost";
    private static final String PROXYPORT = "org.warlock.spine.proxyport";
    private Sendable sendable = null;
    private String responseHeader = null;

    Transmitter(Sendable s) {
        sendable = s;
    }

    @Override
    public void run() {
        ConnectionManager c = ConnectionManager.getInstance();
        if (!sendable.recordTry()) {
            if (sendable.getMessageId() != null) {
                c.removeRequest(sendable.getMessageId());
                sendable.expire();
            }
            return;
        }

        SpineSecurityContext tlsContext = c.getSecurityContext();
//        SSLSocketFactory sf = tlsContext.getSocketFactory();
        String h = sendable.getResolvedUrl();
        String host = null;
        int port = 443;
        try {
            if (h == null) {
                // Retry of persisted reliable message from previous MHS session
                //
                host = ((org.warlock.spine.messaging.EbXmlMessage) sendable).getHost();
            } else {
                sendable.persist();
                URL u = new URL(h);
                host = u.getHost();
                port = (u.getPort() == -1) ? u.getDefaultPort() : u.getPort();
            }
            //Override host and port when using Proxy
            String proxyhost = System.getProperty(PROXYHOST);
            if(proxyhost != null && (proxyhost.trim().length() != 0)){
                host = proxyhost;
            }
            String p = System.getProperty(PROXYPORT);
            if ((p != null) && (p.trim().length() != 0)) {
                try {
                    int proxyport = Integer.parseInt(p);
                    port = proxyport;
                } catch (NumberFormatException e) {
                    System.err.println("Asynchronous wait period not a valid integer - " + e.toString());
                }
            }
            try (Socket s = tlsContext.createSocket(host, port)) {
                int replyLength = -1;
                SessionCaptor sc = c.getSessionCaptor();
                if (sc == null) {
                    sendable.write(s.getOutputStream());
                    replyLength = getHeader(s.getInputStream());
                    if (replyLength == -1) {
                        SpineToolsLogger.getInstance().log("org.warlock.spine.connection.Transmitter.noResponse", "Could not read response sending " + sendable.getMessageId());
                        s.close();
                        return;
                    }
                    if (replyLength > 0)
                        readSynchronousReply(s.getInputStream(), replyLength);
                } else {
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    TeeOutputStream tos = new TeeOutputStream(s.getOutputStream(), outStream);
                    sendable.write(tos);
                    sendable.setOnTheWireRequest(outStream.toByteArray());
                    ByteArrayOutputStream inStream = new ByteArrayOutputStream();
                    TeeInputStream tis = new TeeInputStream(s.getInputStream(), inStream);                    
                    replyLength = getHeader(tis);
                    if (replyLength == -1) {
                        SpineToolsLogger.getInstance().log("org.warlock.spine.connection.Transmitter.noResponse", "Could not read response sending " + sendable.getMessageId());
                        s.close();
                        sc.capture(sendable);
                        return;
                    }
                    if (replyLength > 0) {
                        readSynchronousReply(tis, replyLength);
                        sendable.setOnTheWireResponse(inStream.toByteArray());
                    }
                    sc.capture(sendable);
                }
            }
            if (sendable.getType() == Sendable.SOAP) {
                if (sendable.getSynchronousResponse() == null) {
                    SpineToolsLogger.getInstance().log("org.warlock.spine.connection.Transmitter.noResponseReceived", "No response to " + sendable.getMessageId());
                    return;
                }
                SynchronousResponseHandler handler = c.getSynchronousResponseHandler(sendable.getSoapAction());
                handler.handle((SpineSOAPRequest) sendable);
                return;
            }
            if (sendable.getMessageId() != null) { // Don't do this for asynchronous acks
                if (sendable.getSynchronousResponse() != null) {
                    if ((responseHeader != null) && (responseHeader.contains("HTTP 5"))) {
                        SpineToolsLogger.getInstance().log("org.warlock.spine.connection.Transmitter.HTTP500received", "HTTP 500 received sending " + sendable.getMessageId());
                        c.removeRequest(sendable.getMessageId());

                    } else {
                        if (sendable.getSynchronousResponse().contains(sendable.getMessageId())) {
                            c.registerAck(sendable.getMessageId());
                        }
                        if (sendable.getSynchronousResponse().contains("Bad request")) {
                            c.registerAck(sendable.getMessageId());
                            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.Transmitter.HTTP500received", "Bad request received sending " + sendable.getMessageId());
                        }
                    }
                }
            }
        } catch (Exception eIo) {
            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.Transmitter.IOException", "IOException sending " + sendable.getMessageId() + eIo.getMessage());
        }
    }
    
    private void readSynchronousReply(InputStream is, int replyLength) 
    {
        // Read the response. If the request was synchronous, process the response
        // using the handler. Otherwise do ebXML ack processing.
        //
        byte[] buffer = new byte[replyLength];
        int rd = 0;
        int r = -1;
        do {
            try {
                r = is.read(buffer, rd, replyLength - rd);
            }
            catch (IOException e) {
                SpineToolsLogger.getInstance().log("IOException caught reading reply: " + e.toString());
                break;
            }
            if (r == -1) {
                SpineToolsLogger.getInstance().log("Unexpected EOF after reading " + rd + " of " + replyLength + " bytes");
                break;
            }
            rd += r;
        } while (replyLength > rd);
        sendable.setSynchronousResponse(new String(buffer));
    }
    
    private int getHeader(InputStream is)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        int length = -1;
        String line = null;
        do {
            line = readLine(is);
            if (line == null) {
                break;
            }
            if (line.toLowerCase().contains("content-length: ")) {
                String l = line.substring("content-length: ".length()).trim();
                length = Integer.parseInt(l);
            }
            sb.append(line);
        } while (line.length() != 0);
        responseHeader = sb.toString();
        return length;
    }

    private String readLine(InputStream is)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        do {
            i = is.read();
            if (i == -1) {
                break;
            }
            if (i == 0xa) // \n
            {
                break;
            }
            if (i != 0xd) {  // \r
                sb.append(new Character((char) i));
            }
        } while (true);
        return sb.toString();
    }
}
