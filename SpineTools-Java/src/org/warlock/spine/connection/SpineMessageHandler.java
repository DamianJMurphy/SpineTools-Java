/*

 Copyright 2014 Health and Social Care Information Centre
 Solution Assurance <damian.murphy@hscic.gov.uk>

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

import org.warlock.spine.logging.SpineToolsLogger;
import org.warlock.spine.messaging.EbXmlAcknowledgment;
import org.warlock.spine.messaging.EbXmlMessage;
import org.warlock.spine.messaging.SpineEbXmlHandler;
import java.net.Socket;
import java.net.URL;
//import javax.net.ssl.SSLSocket;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
//import javax.net.ssl.SSLSocketFactory;

/**
 * Thread to handle the receipt of an ebXML message and its acknowledgment. The
 * handler must have an implementation of <code>EbXMLMessageReceiver</code>
 * passed to it in the constructor, to which it will pass the received message
 * after any acknowledgment has been returned.
 *
 * @author Damian Murphy <murff@warlock.org>
 */
public class SpineMessageHandler
        extends Thread {

    private static final String EBXMLERROR = "urn:oasis:names:tc:ebxml-msg:service/MessageError";
    private static final String EBXMLACK = "urn:oasis:names:tc:ebxml-msg:service/Acknowledgment";
    //private SSLSocket socket = null;
    private Socket socket = null;
    private Listener listener = null;

    //public SpineMessageHandler(Listener l, SSLSocket s) {
    public SpineMessageHandler(Listener l, Socket s) {
        socket = s;
        listener = l;
    }
    // private string to contain a reference to originating message Id for logging in test harness mode ONLY
    private String refMessage = null;

    private String getLine()
            throws Exception {
        InputStream in = socket.getInputStream();
        StringBuilder sb = new StringBuilder();
        int r = 0;
        while ((r = in.read()) != (int) '\r') {
            if (r == -1) {
                break;
            }
            sb.append((char) r);
        }
        r = in.read();
        return sb.toString();
    }

    @Override
    public void run() {
        ConnectionManager cm = ConnectionManager.getInstance();
        boolean readingHeader = true;
        String line = null;
        int clen = -1;
        String soapAction = null;
        byte[] buffer = null;
        try {
            StringBuilder sb = new StringBuilder();
            while (readingHeader) {
                line = getLine();
                if (line != null) {
                    sb.append(line);
                    sb.append("\r\n");
                    if (line.trim().length() == 0) {
                        readingHeader = false;
                    } else {
                        String clc = line.toLowerCase();
                        if (clc.contains("content-length")) {
                            String[] p = clc.split(":");
                            clen = Integer.parseInt(p[1].trim());
                        }
                        if (clc.contains("soapaction:")) {
                            soapAction = line.substring(line.indexOf(": ") + 1).trim();
                        }
                    }
                }
            }
            System.out.println();
            int left = clen;
            int right = 0;
            int r = -1;

            buffer = new byte[clen];
            while (left > 0) {
                r = socket.getInputStream().read(buffer, right, left);
                left -= r;
                right += r;
                if (r == -1) {
                    System.err.println("EOF");
                    left = 0;
                }
            }
            // Make an "ebXML object" and return an ack from it
            //
            sb.append(new String(buffer));
            String message = sb.toString();
            if (ConditionalCompilationControls.TESTHARNESS) {
                if (ConditionalCompilationControls.otwMessageLogging) {
                    refMessage = message;
                    SpineToolsLogger.getInstance().log("org.warlock.spine.messaging.sendable.message", "\r\nON THE WIRE INBOUND: \r\n\r\n" + message
                    );
                }
            }

            // See what sort of thing we've received. Handle (in order)
            // asynchronous ebXML ack, Spine SOAP, or ebXML message
            StringBuilder response = null;
            String ack = null;
            if (ConditionalCompilationControls.TESTHARNESS) {
                final String SYNCRESPONSECOUNTDOWN = "org.warlock.spine.syncresponsecountdown";
                String prop = System.getProperty(SYNCRESPONSECOUNTDOWN);
                if (prop != null && prop.trim().toLowerCase().equals("y")) {
                    if (ConditionalCompilationControls.synccounter++ % 2 != 0) {
                        return;
                    }
                }
            }

            if (soapAction == null) {
                SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SpineMessageHandler.noSoapAction", "SOAPaction not found in received message");
                doSynchronousResponse("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n", "");
                socket.close();
            } else {
                // "contains" to allow for some systems quoting the soap action
                if (soapAction.contains(EBXMLACK) || soapAction.contains(EBXMLERROR)) {
                    String ackedId = getAckedMessageId(message);
                    if (ackedId == null) {
                        SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SpineMessageHandler.noAckedId", "Could not resolve RefToMessageId in received asynchronous acknowledgment");
                    } else {
                        cm.registerAck(ackedId);
                    }
                    response = new StringBuilder("HTTP/1.1 200 OK\r\nContent-Length: 0");
                    response.append("\r\nConnection: close\r\nContent-Type: text/xml\r\nSOAPAction: urn:urn:oasis:names:tc:ebxml-msg:service/Acknowledgment\r\n\r\n");
                    doSynchronousResponse(response.toString(), "");
                    socket.close();
                } else {
                    // In TEST_HARNESS mode allow a soapFault response to be configured
                    if (ConditionalCompilationControls.TESTHARNESS) {
                        final String SOAPFAULT = "org.warlock.spine.connection.soapfault";
                        String prop = System.getProperty(SOAPFAULT);
                        if (prop != null && prop.trim().toLowerCase().equals("y")) {
                            doSynchronousResponse("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n", "");
                            socket.close();
                            return;
                        }
                    }
                    if (isSpineSOAP(soapAction)) {

                        // The code below is "true" for the SpineTools being used to implement a
                        // Spine client. Be careful making it not true...
                        doSynchronousResponse("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n", "");
                        socket.close();
                        throw new UnsupportedOperationException("Spine-client SpineSOAP not implemented: nothing does this (yet)");

                    } else {
                        EbXmlMessage msg = new EbXmlMessage(new ByteArrayInputStream(message.getBytes()));
                        Exception ebxmlException = msg.getParseException();
                        boolean duplicate = listener.receiveId(msg);
                        boolean synchronousAck = msg.getHeader().getSyncReply();
                        // In TEST_HARNESS mode allow a negative ebXML response to be configured
                        if (ConditionalCompilationControls.TESTHARNESS) {
                            final String NEGEBXMLACK = "org.warlock.spine.connection.negativeebxmloverride";
                            String prop = System.getProperty(NEGEBXMLACK);
                            if (prop != null && prop.trim().toLowerCase().equals("y")) {
                                ebxmlException = new Exception();
                            }
                        }
                        if (ebxmlException == null) {
                            ack = msg.makeEbXmlAck(!synchronousAck);
                        } else {
                            ack = msg.makeEbXmlNack("1000", ebxmlException.getMessage(), "ebXml Parser");
                        }
                        String asyncAck = null;
                        if (!synchronousAck) {
                            asyncAck = ack;
                            ack = "";
                        }
                        if (ack.length() == 0) {
                            response = new StringBuilder("HTTP/1.1 200 OK\r\nContent-Length: 0");
                            response.append("\r\nConnection: close\r\nContent-Type: text/xml\r\nSOAPAction: urn:urn:oasis:names:tc:ebxml-msg:service/Acknowledgment\r\n\r\n");
                        } else {
                            if (ebxmlException == null) {
                                response = new StringBuilder("HTTP/1.1 202 OK\r\nContent-Length: ");
                            } else {
                                response = new StringBuilder("HTTP/1.1 500 Internal Server Error\r\nContent-Length: ");
                            }
                            response.append(ack.length());
                            response.append("\r\nConnection: close\r\nContent-Type: text/xml\r\nSOAPAction: urn:urn:oasis:names:tc:ebxml-msg:service/Acknowledgment\r\n\r\n");
                        }
                        doSynchronousResponse(response.toString(), ack);
                        socket.close();
                        if (asyncAck != null) {
                            doAsynchronousAck(asyncAck);
                        }
                        // Only call the handler if we've not seen this one before.
                        //
                        if (ConditionalCompilationControls.DUMP_RECEIVED_MESSAGE) {
                            System.out.append(message);
                        }
                        if (duplicate) {
                            return;
                        }
                        if (ebxmlException != null) {
                            return;
                        }
                        SpineEbXmlHandler handler = (SpineEbXmlHandler) cm.getEbXmlHandler(soapAction);
                        if (handler != null) {
                            handler.handle(msg);
                        } else {
                            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SpineMessageHandler.noHandler", "Could not resolve message handler, and error initialising the default one");
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            if (!socket.isClosed()) {
                try {
                    doSynchronousResponse("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n", "");
                    socket.close();
                    SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SpineMessageHandler.run-clientnotified", e);
                } catch (Exception eLast) {
                    SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SpineMessageHandler.run-notifyingclient", e);
                }
            } else {
                SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SpineMessageHandler.run-client-not-notified", e);
            }
        }
    }

    /**
     * Sends the synchronous response.
     *
     * @param r response header
     * @param a response body
     * @throws Exception
     */
    private void doSynchronousResponse(String r, String a)
            throws Exception {
        OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream());
        osw.write(r);
        osw.flush();
        if (a == null) {
            osw.write("");
        } else {
            osw.write(a);
        }
        osw.flush();
        if (ConditionalCompilationControls.TESTHARNESS) {
            if (ConditionalCompilationControls.otwMessageLogging) {
                String originatingMessageId = null;
                if (refMessage == null) {
                    originatingMessageId = null;
                } else {
                    int start = refMessage.indexOf("MessageId>");
                    if (start == -1) {
                        originatingMessageId = null;
                    } else {
                        start += "MessageId>".length();
                        int end = refMessage.indexOf("<", start);
                        if (end == -1) {
                            originatingMessageId = null;
                        } else {
                            originatingMessageId = refMessage.substring(start, end);
                        }
                    }
                }

                SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SpineMessageHandler.message", "\r\nReference to Message ID " + originatingMessageId + " - ON THE WIRE SYNC OUTBOUND: \r\n\r\n" + r);
                if (a != null && !a.equals("")) {
                    SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SpineMessageHandler.message", "\r\n\r\n" + a);
                }
            }
        }

    }

    private String getAckedMessageId(String message) {

        if (message == null) {
            return null;
        }
        int start = message.indexOf("RefToMessageId>");
        if (start == -1) {
            return null;
        }
        start += "RefToMessageId>".length();
        int end = message.indexOf("<", start);
        if (end == -1) {
            return null;
        }
        return message.substring(start, end);
    }

    /**
     * Create and return an asynchronous ebXML acknowledgment.
     *
     * @param ack
     */
    private void doAsynchronousAck(String ack) {
        if (ConditionalCompilationControls.TESTHARNESS) {
            final String ASYNCRESPONSEDELAYCOUNTDOWN = "org.warlock.spine.asyncresponsedelaycountdown";
            String prop = System.getProperty(ASYNCRESPONSEDELAYCOUNTDOWN);
            if (prop != null && prop.trim().toLowerCase().equals("y")) {
                long asynchronousResponseDelay = 0;
                final String ASYNCHRONOUSRESPONSEDELAY = "org.warlock.spine.asynchronousebxmlreply.delay";
                String srd = System.getProperty(ASYNCHRONOUSRESPONSEDELAY);
                if (srd != null) {
                    try {
                        asynchronousResponseDelay = Long.parseLong(srd) * 1000;
                    } catch (Exception e) {
                        System.err.println("Warning: Error setting asynchronous response delay: " + srd);
                    }
                }
                if (asynchronousResponseDelay != 0) {
                    try {
                        Thread.sleep(asynchronousResponseDelay);
                    } catch (Exception e) {
                        System.err.println("Warning: asynchronousResponseDelay sleep() interrupted.");
                    }
                }
            }
            if(ConditionalCompilationControls.otwMessageLogging){refMessage = ack;}
        }
        try {
            ConnectionManager c = ConnectionManager.getInstance();
            SpineSecurityContext tlsContext = c.getSecurityContext();
            // SSLSocketFactory sf = tlsContext.getSocketFactory();            
            EbXmlAcknowledgment ebxmlack = new EbXmlAcknowledgment(ack);
            URL u = new URL(ebxmlack.getResolvedUrl());
            // try (SSLSocket s = (SSLSocket)sf.createSocket(u.getHost(), 443)) {
            // URL.getPort() is borken and returns -1 for https://something when the 
            // default port is assumed from the scheme.
            try (Socket s = tlsContext.createSocket(u.getHost(), (u.getPort() == -1) ? u.getDefaultPort() : u.getPort())) {
                //s.startHandshake();
                ebxmlack.write(s.getOutputStream());
                int rlength = getHeader(s.getInputStream());
                if (rlength == -1) {
                    SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SpineMessageHandler.doAsynchronousAck", "Failed to get HTTP response to asynchronous ack");
                }
            }
        } catch (Exception e) {
            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SpineMessageHandler.doAsynchronousAck", e);
        }

    }

    /**
     * Reads a single line from the given InputStream. This is used rather than
     * a BufferedReader to retain control over the read position on the stream.
     *
     * @param is
     * @return
     * @throws IOException
     */
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

    /**
     * Reads a received HTTP header.
     *
     * @param is InputStream from where the header is read
     * @return Content-length value
     * @throws IOException
     */
    private int getHeader(InputStream is)
            throws IOException {
        int length = -1;
        String line = null;
        StringBuilder sb = new StringBuilder();
        do {
            line = readLine(is);
            sb.append(line).append("\r\n");
            if (line == null) {
                break;
            }
            if (line.toLowerCase().contains("content-length: ")) {
                String l = line.substring("content-length: ".length()).trim();
                length = Integer.parseInt(l);
            }

        } while (line.length() != 0);

        if (ConditionalCompilationControls.TESTHARNESS) {
            if (ConditionalCompilationControls.otwMessageLogging) {
                String originatingMessageId = null;
                if (refMessage == null) {
                    originatingMessageId = null;
                } else {
                    int start = refMessage.indexOf("MessageId>");
                    if (start == -1) {
                        originatingMessageId = null;
                    } else {
                        start += "MessageId>".length();
                        int end = refMessage.indexOf("<", start);
                        if (end == -1) {
                            originatingMessageId = null;
                        } else {
                            originatingMessageId = refMessage.substring(start, end);
                        }
                    }
                }
                SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SpineMessageHandler.message", "\r\nReference to Message ID " + originatingMessageId + " - ON THE WIRE SYNC INBOUND: \r\n\r\n" + sb.toString());
            }
        }

        return length;
    }

    /**
     * This method exists principally for future-proofing if there is ever a
     * need to use this code to handle Spine synchronous requests.
     *
     * @param sa SOAP action
     * @return False
     */
    private boolean isSpineSOAP(String sa) {
        return false;
    }
}
