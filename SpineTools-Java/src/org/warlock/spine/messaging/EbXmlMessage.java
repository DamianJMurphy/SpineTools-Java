/* Copyright 2013 Health and Social Care Information Centre,
 *  Solution Assurance <damian.murphy@nhs.net>

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

package org.warlock.spine.messaging;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import org.warlock.spine.connection.ConnectionManager;

import org.warlock.spine.connection.SdsTransmissionDetails;
import org.warlock.spine.connection.ConditionalCompilationControls;
import org.warlock.spine.logging.SpineToolsLogger;

/**
 *Concrete Sendable class representing a Spine asynchronous ebXML message.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class EbXmlMessage extends Sendable {

    private static String FIRSTMIMEPREFIX = "--";
    private static String MIMEPREFIX = "\r\n--";
    private static String MIMEPOSTFIX = "--";
    private static int MAX_MESSAGE_SIZE = 5242880; // 5MB Spine maximum message size for ebXML messages
    private static String HTTPHEADER = "POST __CONTEXT_PATH__ HTTP/1.1\r\nHost: __HOST__\r\nSOAPAction: \"__SOAP_ACTION__\"\r\nContent-Length: __CONTENT_LENGTH__\r\nContent-Type: multipart/related; boundary=\"__MIME_BOUNDARY__\"; type=\"text/xml\"; start=\"<__START_ID__>\"\r\nConnection: close\r\n\r\n";

    private EbXmlMessage response = null;
    private EbXmlHeader header = null;
    private SpineHL7Message hl7message = null;
    private ArrayList<Attachment> attachments = null;
    private String mimeboundary = "--=_MIME-Boundary";
    private String host = null;
    
    private String receivedHost = "SPINE_RELIABLE_MESSAGE_HOST";
    private String receivedContextPath = "EXPIRED_PERSISTED_RELIABLE_MESSAGE";
    
    private boolean persist = false;
    private boolean hasBeenPersisted = false;
    private File persistenceFile = null;
    
    private static String acktemplate = null;
    private static String nacktemplate = null;
    private static final String ISO8601FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final SimpleDateFormat DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private Exception parseException = null;

    /**
     * Used to assemble an EbXmlMessage from the given Stream, which has been
     * accepted from the network. This expects to get the inbound HTTP stream
     * (or at least an SslStream) from the start, because it begins processing
     * with the HTTP POST.
     *
     * @param instream Input stream from network of message being received
     * @throws Exception if first loading acknowledgment and error templates, or
     * parsing fails.
     */
    public EbXmlMessage(InputStream instream)
            throws Exception {
        synchronized (this) {
            if (acktemplate == null) {
                try {
                    acktemplate = readTemplate("ebxmlacktemplate.txt");
                } catch (Exception e) {
                    System.err.println("FATAL: Cannot load ebXML ack template: " + e.toString());
                    throw e;
                }
            }
            if (nacktemplate == null) {
                try {
                    nacktemplate = readTemplate("ebXMLError_template.xml");
                } catch (Exception e) {
                    System.err.println("FATAL: Cannot load ebXML error template: " + e.toString());
                    throw e;
                }
            }
        }

        // Assemble from network - note that Spine messages are NOT chunked
        //
        String headerline = null;
        StringBuilder hdr = new StringBuilder();
        HashMap<String, String> headers = new HashMap<>();
        while ((headerline = readLine(instream)).trim().length() != 0) {
            if (headerline.startsWith("POST")) {
                // Frist line, grab the context path
                int firstSpace = headerline.indexOf(" ");
                int secondSpace = headerline.indexOf(" ", firstSpace + 1);
                if ((firstSpace == -1) || (secondSpace == -1))
                    throw new Exception("Malformed HTTP request line, can't parse POST context path");
                receivedContextPath = headerline.substring(firstSpace, secondSpace);
            } else {
                if (!((headerline.startsWith("\t") || (headerline.startsWith(" "))))) {
                    // This line isn't a continuation line, so process the previous line
                    // if we have one
                    //
                    if (!(hdr.length() == 0)) {
                        String h = hdr.toString();
                        int colon = h.indexOf(":");
                        if (colon == -1) {
                            throw new Exception("Malformed HTTP header - no field/data delimiter colon");
                        }
                        headers.put(h.substring(0, colon).toUpperCase(), h.substring(colon + 1).trim());                                            
                        // Then refresh the buffer
                        hdr = new StringBuilder();
                    }
                } 
                // Save the current line
                hdr.append(headerline);        
            }
        }
        // Reached the end of the header, but without processing the last line. So
        // process it now.
        //
        String h = hdr.toString();
        int colon = h.indexOf(":");
        if (colon == -1) {
            throw new Exception("Malformed HTTP header - no field/data delimiter colon");
        }
        headers.put(h.substring(0, colon).toUpperCase(), h.substring(colon + 1).trim());                                            
        
        String ctype = headers.get("CONTENT-TYPE");
        if (ctype == null) {
            throw new Exception("Malformed HTTP headers - no Content-Type found");
        }
        if (ctype.contains("multipart/related")) {
            mimeboundary = parseMimeBoundary(ctype);
        }
        host = headers.get("HOST");
        receivedHost = host;
        String clen = headers.get("CONTENT-LENGTH");
        if (clen == null) {
            throw new Exception("Malformed HTTP headers - no Content-Length found");
        }
        int contentlength = Integer.parseInt(clen);
        soapAction = headers.get("SOAPACTION");
        soapAction = soapAction.replace('"', ' ').trim();

            // There is a bug in Spine-hosted services that turns a SOAPaction starting with
        // "urn:" into "urn:urn:" - fix this if we find it.
        //
        if (soapAction.startsWith("urn:urn:")) {
            soapAction = soapAction.substring(4);
        }

            // Read content-length bytes and parse out the various parts of the 
        // received message. 
        //
        byte[] wire = new byte[contentlength];
        int bytesRead = 0;
        while (bytesRead < contentlength) {
            bytesRead += instream.read(wire, bytesRead, contentlength - bytesRead);
        }
        String msg = new String(wire);

            // Split on the mimeboundary. "msg" doesn't contain the HTTP headers so we should
        // just be able to walk through the attachments. If we can't, report an exception
        //
        int startPosition = msg.indexOf(mimeboundary, 0);
        if (startPosition == -1) {
                // Need to handle the case where the content is
            // actually an asynchronous ebXML ack.
            //
            // If content-type is text/xml and soapaction contains Acknowledgment or MessageError,
            // it is an ack/nack. If we get one of these we need to log it and tell the connection
            // manager about it. But we don't need to do any further processing.
            //
            if (ctype.toLowerCase().startsWith("text/xml")) {
                if (soapAction.contains("Acknowledgment")) {
                    // Remove from requests, and exit
                    String a = EbXmlAcknowledgment.getAckedMessageId(msg);
                    if (a == null) {
                        SpineToolsLogger.getInstance().log("org.warlock.spine.messaging.EbXmlMessage.parseError", "Failed to extract message id reference from Acknowledgment");
                        return;
                    }
                    ConnectionManager cm = ConnectionManager.getInstance();
                    cm.registerAck(a);
                    return;
                }
                if (soapAction.contains("MessageError")) {
                    // Remove from requests, and exit
                    String a = EbXmlAcknowledgment.getAckedMessageId(msg);
                    if (a == null) {
                        SpineToolsLogger.getInstance().log("org.warlock.spine.messaging.EbXmlMessage.parseError", "Failed to extract message id reference from MessageError");
                        return;
                    }
                    SpineToolsLogger.getInstance().log("org.warlock.spine.messaging.EbXmlMessage.MessageError", "MessageError received for " + a);
                    ConnectionManager cm = ConnectionManager.getInstance();
                    cm.registerAck(a);
                    return;
                }
            }
            throw new Exception("Malformed message");
        }
        int endPosition = 0;
        int partCount = 0;
        boolean gotEndBoundary = false;
        do {
            startPosition += mimeboundary.length();
            endPosition = msg.indexOf(mimeboundary, startPosition);
            if (endPosition == -1) {
                gotEndBoundary = true;
            } else {
                switch (partCount) {
                    case 0:
                        header = new EbXmlHeader(msg.substring(startPosition, endPosition));
                        if (header.getTimestamp() != null) {
                            try {
                                SimpleDateFormat fmt = new SimpleDateFormat(ISO8601FORMAT);
                                started.setTime(fmt.parse(header.getTimestamp()));
                            }
                            catch (NumberFormatException nfe) {
                                System.err.append(header.getTimestamp());
                                throw nfe;
                            }
                            // We don't know how many attempts were actually made, so assume
                            // one try at the start time.
                            //
                            lastTry = (java.util.Calendar)started.clone();
                            tries = 1;
                        }
                        break;
                    case 1:
                        hl7message = new SpineHL7Message(msg.substring(startPosition, endPosition));
                        break;
                    default:
                        if (attachments == null) {
                            attachments = new ArrayList<>();
                        }
                            // IMPROVEMENT: Make this more flexible to be able to support multiple types of
                        // ITK trunk message, just in case
                        //
                        if (soapAction.contains("COPC_IN000001GB01")) {
                            try {
                                attachments.add(new ITKDistributionEnvelopeAttachment(msg.substring(startPosition, endPosition)));
                            } catch (Exception e) {
                                parseException = e;
                            }
                        } else {
                            attachments.add(new GeneralAttachment(msg.substring(startPosition, endPosition)));
                        }
                        break;
                }
                partCount++;
                startPosition = endPosition;
            }
        } while (!gotEndBoundary);
        persistDuration = ConnectionManager.getInstance().getPersistDuration(header.getSvcIA());
    }

    
    
    private String readTemplate(String tname)
            throws Exception {
        StringBuilder sb;
        try (InputStream is = getClass().getResourceAsStream(tname)) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            sb = new StringBuilder();
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append("\r\n");
            }
        }
        return sb.toString();
    }

    public String getHost() { return host; }
    
    public Exception getParseException() {
        return parseException;
    }

    public void addAttachment(Attachment a) {
        attachments.add(a);
    }

    public String makeEbXmlNack(String ecode, String ecodecontext, String edesc)
            throws Exception {
        if (!header.ackRequested()) {
            return "";
        }
        if (!header.getDuplicateElimination()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(nacktemplate);
        substitute(sb, "__FROMPARTY__", ConnectionManager.getInstance().getMyPartyKey());
        substitute(sb, "__TOPARTY__", header.getFromPartyKey());
        substitute(sb, "__CPAID__", header.getCpaId());
        substitute(sb, "__CONVERSATIONID__", header.getConversationId());
        substitute(sb, "__MESSAGEID__", UUID.randomUUID().toString().toUpperCase());
        substitute(sb, "__TIMESTAMP__", DATEFORMAT.format(new Date()));
        substitute(sb, "__REFTOMESSAGEID__", getMessageId());
        substitute(sb, "__ERROR_LIST_ID__", UUID.randomUUID().toString());
        substitute(sb, "__ERROR_ID__", UUID.randomUUID().toString());
        substitute(sb, "__ERROR_ERRORCODE_REQUIRED__", (ecode != null) ? ecode : "");
        substitute(sb, "__ERROR_CODECONTEXT__", (ecodecontext != null) ? ecodecontext : "");
        substitute(sb, "__ERROR_DESCRIPTION__", (edesc != null) ? edesc : "");
        return sb.toString();

    }

    /**
     * Generate the body of an ebXML acknowledgement with the appropriate
     * identifiers, references and timestamps. Note that this does NOT make any
     * judgements as to whether the acknowledgement should be returned (that is,
     * it does not know whether the message is reliable or not), nor about
     * whether the acknowledgement is returned synchronously or asynchronously.
     *
     * The acknowledgement is made from the template at
     * org.warlock.spine.messaging.ebxmlacktemplate.xml which is part of the
     * package contents.
     *
     * @param replaceCpaId
     * @return String containing the ebXML acknowledgement, if any, to be
     * returned for this message.
     * @throws Exception if anything goes wrong making the acknowledgement.
     */
    public String makeEbXmlAck(boolean replaceCpaId)
            throws Exception {
        if (!header.ackRequested()) {
            return "";
        }
        if (!header.getDuplicateElimination()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(acktemplate);
        substitute(sb, "__FROMPARTY__", ConnectionManager.getInstance().getMyPartyKey());
        substitute(sb, "__TOPARTY__", header.getFromPartyKey());
        if (replaceCpaId) {
            substitute(sb, "__CPAID__", header.getCpaId());
        }
        substitute(sb, "__CONVERSATIONID__", header.getConversationId());
        substitute(sb, "__MESSAGEID__", UUID.randomUUID().toString().toUpperCase());
        substitute(sb, "__TIMESTAMP__", DATEFORMAT.format(new Date()));
        substitute(sb, "__REFTOMESSAGEID__", getMessageId());
        return sb.toString();
    }

    @Override
    public String getHl7Payload() {
        return hl7message.getHL7Payload();
    }

    @Override
    public String getResolvedUrl() {
        return resolvedUrl;
    }

    /**
     * Internal call to parse the HTTP header content-type's "boundary" clause
     * to determine the MIME part boundary
     */
    private String parseMimeBoundary(String ctype)
            throws Exception {
        StringBuilder mb = new StringBuilder("--");
        int boundary = ctype.indexOf("boundary");
        if (boundary == -1) {
            return "";
        }
        boundary += 8; // "boundary".length
        int startBoundary = 0;
        int endBoundary = 0;
        while (boundary < ctype.length()) {
            if (startBoundary == 0) {
                if (ctype.toCharArray()[boundary] == '=') {
                    boundary++;
                    if (ctype.toCharArray()[boundary] == '"') {
                        boundary++;
                    }
                    startBoundary = boundary;
                    boundary++;
                    continue;
                }
                throw new Exception("Invalid Content-Type: MIME boundary not properly defined (spaces ?)");
            } else {
                char c = ctype.toCharArray()[boundary];
                switch (c) {
                    case ';':
                    case '"':
                        endBoundary = boundary;
                        break;
                    case ' ':
                        throw new Exception("Invalid Content-Type: MIME boundary not properly defined (spaces ?)");
                    default:
                        break;

                }
            }
            if (endBoundary == 0) {
                boundary++;
            } else {
                break;
            }
        }
        if (endBoundary == 0) {
            mb.append(ctype.substring(startBoundary));
        } else {
            mb.append(ctype.substring(startBoundary, endBoundary));
        }
        return mb.toString();
    }

    /**
     * Internal call to read HTTP headers without a .Net buffered reader messing
     * up the rest of the stream content.
     */
    private String readLine(InputStream s)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        int c = -1;
        while ((c = s.read()) != '\n') {
            if (c == -1) {
                break;
            } else {
                if (c != '\r') {
                    sb.append((char) c);
                }
            }
        }
        sb.append((char) c);
        return sb.toString();
    }

    /**
     * Construct an EbXmlMessage for sending.
     *
     * @param s An SdSTransmissionDetails instance for recipient information
     * @param m SpineHL7Message instance to be sent
     *
     * @throws Exception
     */
    public EbXmlMessage(SdsTransmissionDetails s, SpineHL7Message m)
            throws Exception {
        synchronized (this) {
            if (acktemplate == null) {
                try {
                    acktemplate = readTemplate("ebxmlacktemplate.txt");
                } catch (Exception e) {
                    System.err.println("FATAL: Cannot load ebXML ack template: " + e.toString());
                    throw e;
                }
            }
            if (nacktemplate == null) {
                try {
                    nacktemplate = readTemplate("ebXMLError_template.xml");
                } catch (Exception e) {
                    System.err.println("FATAL: Cannot load ebXML error template: " + e.toString());
                    throw e;
                }
            }
        }

        ConnectionManager cm = ConnectionManager.getInstance();
        header = new EbXmlHeader(this, s);
        header.setMyPartyKey(cm.getMyPartyKey());
        type = EBXML;
        hl7message = m;
        String svcurl = cm.resolveUrl(s.getSvcIA());
        if (svcurl == null) {
            resolvedUrl = s.getUrl();
        } else {
            resolvedUrl = svcurl;
        }
        if (s.getRetries() != SdsTransmissionDetails.NOT_SET) {
            retryCount = s.getRetries();
            minRetryInterval = s.getRetryInterval();
            persistDuration = s.getPersistDuration();
        }
        attachments = new ArrayList<>();
        persist = (s.getRetries() > 0);
    }

    @Override
    public void persist() 
            throws IOException
    { 
        if (ConditionalCompilationControls.TESTHARNESS)
            return;
        if (!persist)
            return;
        if (hasBeenPersisted)
            return;
        
        persistenceFile = new File(ConnectionManager.getInstance().getMessageDirectory(), header.getMessageId());
        try (FileOutputStream fs = new FileOutputStream(persistenceFile)) {
            try {
                write(fs);
                hasBeenPersisted = true;
            }
            catch (Exception e) {
                // TODO: Log instead of throwing an exception. persist() is called just before
                // a transmission attempt, but it only does anything if "hasBeenPersisted" is 
                // false. If the call to write() above, fails, then hasBeenPersisted is still
                // false, so the next time it is retried out of memory it will be persisted again
                // and if the transmission attempt actually worked, then the persistent copy
                // would have been deleted anyway.
                //
                throw new IOException("Persisting message", e);
            }
        }
    }
    
    @Override
    public void setResponse(Sendable r) {
        response = (EbXmlMessage) r;
    }

    @Override
    public Sendable getResponse() {
        return response;
    }

    @Override
    public String getMessageId() {
        return header.getMessageId();
    }

    @Override
    public void setMessageId(String id) {
        header.setMessageId(id);
    }

    @Override
    public void write(OutputStream s)
            throws Exception {
        StringBuilder sb = new StringBuilder(FIRSTMIMEPREFIX);
        sb.append(mimeboundary);
        sb.append(header.makeMimeHeader());
        sb.append(header.serialise());
        sb.append(MIMEPREFIX);
        sb.append(mimeboundary);
        sb.append(hl7message.makeMimeHeader());
        sb.append(hl7message.serialise());
        if (attachments != null) {
            for (Attachment a : attachments) {
                sb.append(MIMEPREFIX);
                sb.append(mimeboundary);
                sb.append(a.makeMimeHeader());
                sb.append(a.serialise());
            }
        }
        sb.append(MIMEPREFIX);
        sb.append(mimeboundary);
        sb.append(MIMEPOSTFIX);
        long l = sb.length();
        if (l < MAX_MESSAGE_SIZE) {
            String hdr = makeHttpHeader(l);
            OutputStreamWriter t = new OutputStreamWriter(s);
            t.write(hdr);
            t.flush();
            t.write(sb.toString());
            t.flush();
        } else {
                // NEXT VERSION: Implement this... Bit of an exception case here, may be
            // better to have a caller-settable flag (or a different method) for "send using large
            // message protocol".
            //
            String mainMessage = sendLargeMessage(sb.toString());
        }
    }

    private String sendLargeMessage(String s) {
        throw new UnsupportedOperationException();
    }

    private String makeHttpHeader(long l)
            throws MalformedURLException {
        StringBuilder sb = new StringBuilder(HTTPHEADER);
        if (resolvedUrl == null) {
            // We end up here if we're doing a persisted reliable
            // message. Either it is going to be handled (re-sent) or it
            // is being expired.
            //
            substitute(sb, "__CONTEXT_PATH__", receivedContextPath);
            substitute(sb, "__HOST__", receivedHost);
        } else {
            URL u = new URL(resolvedUrl);
            substitute(sb, "__CONTEXT_PATH__", u.getPath());
            substitute(sb, "__HOST__", u.getHost());
        }
        StringBuilder sa = new StringBuilder(header.getService());
        sa.append("/");
        sa.append(header.getInteractionId());
        soapAction = sa.toString();
        substitute(sb, "__SOAP_ACTION__", soapAction);
        substitute(sb, "__CONTENT_LENGTH__", String.valueOf(l));
        substitute(sb, "__MIME_BOUNDARY__", mimeboundary);
        substitute(sb, "__START_ID__", header.getContentId());
        return sb.toString();
    }

    public EbXmlHeader getHeader() {
        return header;
    }

    public String getMimeBoundary() {
        return mimeboundary;
    }

    public void setMimeBoundary(String value) {
        mimeboundary = value;
    }

    public SpineHL7Message getHL7Message() {
        return hl7message;
    }

    public void setHL7Message(SpineHL7Message value) {
        hl7message = value;
    }

    public ArrayList<Attachment> getAttachments() {
        return attachments;
    }
}
