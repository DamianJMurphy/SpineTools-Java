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
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import org.warlock.spine.connection.SdsTransmissionDetails;

    /**
     * Concrete subclass of Attachment to represent the ebXml header of a Spine asynchronous message. This can
     * either be constructed by parsing an inbound message, or from an EbXmlMessage instance plus SDS
     * transmission details for a message to be sent.
     * 
     * For sending messages, the header itself is made from the "ebxmlheadertemplate.txt" included with the
     * package.
     */ 
public class EbXmlHeader extends Attachment {

    private static final String EBXMLHEADERTEMPLATE = "ebxmlheadertemplate.txt";
    private static final String DUPLICATEELIMINATIONELEMENT = "<eb:DuplicateElimination/>";
    private static final String ACKREQUESTEDELEMENT = "<eb:AckRequested eb:version=\"2.0\" SOAP:mustUnderstand=\"1\" SOAP:actor=\"__SOAP_ACTOR__\" eb:signed=\"false\"/>";
    private static final String SYNCREPLYELEMENT = "<eb:SyncReply eb:version=\"2.0\" SOAP:mustUnderstand=\"1\" SOAP:actor=\"http://schemas.xmlsoap.org/soap/actor/next\"/>";
    private static final String EBXMLHEADERMIMETYPE = "text/xml";

    private static final String EBXMLNS = "http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd";

    private static final SimpleDateFormat ISO8601DATEFORMAT = new SimpleDateFormat("yyyy'-'MM'-'dd'T'HH':'mm':'ss");

    private static String ebxmlheadertemplate = null;

    private EbXmlMessage ebxml = null;

    public String myPartyKey = null;
    private String service = null;
    private String interactionid = null;
//        private String action = null;
    private String cpaid = null;
    private String messageId = null;
    private String conversationId = null;
    private String soapActor = null;
    private String timeStamp = null;
    private String toPartyKey = null;
    private String svcIA = null;

    // Populated for received messages
    private String fromPartyKey = null;
    private boolean duplicateElimination = true;
    private boolean ackrequested = true;
    private boolean syncreply = true;

    private Exception bootException = null;
    private String serialisation = null;

    /**
     * Make an EbXmlHeader from a received message, extracting the various
     * header fields.
     *
     * @param s Received Message
     * @throws java.lang.Exception
     */
    public EbXmlHeader(String s) throws Exception {
            // Build from received message
        init();
            // We get the MIME headers (everything between the delimiting MIME booundary Strings) 
        // - so make a call to Attachment to parse them (we don't care about MIME type here),
        String message = stripMimeHeaders(s);
        
            // Extract duplicate elimination, ackrequested, conversation id, message id,
        // syncreply and CPAid.
        
        duplicateElimination = extractBooleanHeader(message, "DuplicateElimination");
        syncreply = extractBooleanHeader(message, "SyncReply");
        ackrequested = extractBooleanHeader(message, "AckRequested");
        cpaid = getEbXMLElement(message, "CPAId");
        conversationId = getEbXMLElement(message, "ConversationId");
        messageId = getEbXMLElement(message, "MessageId");
        timeStamp = getEbXMLElement(message, "Timestamp");
        interactionid = getEbXMLElement(message, "Action");
        service = getEbXMLElement(message, "Service");
        fromPartyKey = getPartyId(message, "From");
        StringBuilder sb = new StringBuilder(service);
        sb.append(":");
        sb.append(interactionid);
        svcIA = sb.toString();
        serialisation = message;
        setMimeType(EBXMLHEADERMIMETYPE);
    }

    public String getSvcIA() { return svcIA; }
    private String getPartyId(String message, String tag)
            throws Exception
    {
        if ((tag == null) || (tag.trim().length() == 0))
            return "";
        int start = message.indexOf(tag);
        if (start == -1) {
            throw new Exception(tag + " not found");
        }
        start += tag.length();
        start = message.indexOf("PartyId", start);
        if (start == -1) {
            throw new Exception(tag + "PartyId not found");
        }
        while (message.charAt(start) != '>') start++;
        start++;       
        StringBuilder sb = new StringBuilder();
        int end = start;
        while (message.charAt(end) != '<') {
            sb.append(message.charAt(end));
            end++;
        }
        return sb.toString();        
    }
    
    private boolean extractBooleanHeader(String message, String tag)
    {
        try {
            getEbXMLElement(message, tag);
        }
        catch (Exception e) {
            return false;
        }
        return true;    
    }
   private String getEbXMLElement(String message, String tag)
            throws Exception
    {
        if ((tag == null) || (tag.trim().length() == 0))
            return "";
        int start = message.indexOf(tag);
        if (start == -1) {
            throw new Exception(tag + " not found");
        }
        start += tag.length();
        StringBuilder sb = new StringBuilder();
        while (message.charAt(start) != '>') start++;
        start++;
        int end = start;
        while (message.charAt(end) != '<') {
            sb.append(message.charAt(end));
            end++;
        }
        return sb.toString();
    }    
    /**
     * Construct an EbXmlHeader for the given EbXmlMessage, using the
     * transmission details.
     *
     * @param msg The EbXmlMessage for which this will be the header
     * @param s An SdsTransmissionDetails instance containing information on
     * sending this message type to the recipient
     * @throws java.lang.Exception     */
    public EbXmlHeader(EbXmlMessage msg, SdsTransmissionDetails s) throws Exception{
        if (bootException != null) {
            throw bootException;
        }
        init();
        ebxml = msg;
        messageId = UUID.randomUUID().toString().toUpperCase();
        setMimeType(EBXMLHEADERMIMETYPE);
        service = s.getService();
        interactionid = s.getInteractionId();
        cpaid = s.getCPAid();
        soapActor = s.getSoapActor();
        toPartyKey = s.getPartyKey();
        duplicateElimination = s.getDuplicateElimination().equals("always");
        ackrequested = s.getAckRequested().equals("always");
        syncreply = s.getSyncReply().equals("MSHSignalsOnly");
    }

    private void init()
            throws Exception
    {
        synchronized (EBXMLHEADERTEMPLATE) {
            if (ebxmlheadertemplate == null) {
                StringBuilder sb = new StringBuilder();
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader((org.warlock.spine.messaging.SpineHL7Message.class.getResourceAsStream(EBXMLHEADERTEMPLATE))));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                        sb.append("\n");
                    }
                    ebxmlheadertemplate = sb.toString();
                } catch (Exception e) {
                    bootException = e;
                    throw e;
                }
            }
        }        
    }
    
    public void setMyPartyKey(String s) {
        myPartyKey = s;
    }

    @Override
    public String getEbxmlReference() {return "";}

    /**
     *
     * @return SyncReply status of ebXML message
     */
    public boolean getSyncReply(){return syncreply;}

    /**
     *
     * @return Timestamp of ebXML message
     */
    public String getTimestamp(){return timeStamp;}

    /**
     *
     * @param value Timestamp of ebXML message
     */
    public void setTimestamp(String value){timeStamp = value;}

    /**
     *
     * @return SOAPActor of ebXML message
     */
    public String getSoapActor(){return soapActor;}

    /**
     *
     * @param value SOAPActor of ebXML message
     */
    public void setSoapActor(String value){soapActor = value;}

    /**
     *
     * @return TO party key of ebXML message
     */
    public String getToPartyKey(){return toPartyKey;}

    /**
     *
     * @param value To party key of ebXML message
     */
    public void setToPartyKey(String value){toPartyKey = value;}
    
    /**
     *
     * @return From party key of ebXML message
     */
    public String getFromPartyKey(){return fromPartyKey;}

    /**
     *
     * @param value From party key of ebXML message
     */
    public void setFromPartyKey(String value){fromPartyKey = value;}
    
    /**
     *
     * @return Conversation Id of ebXML message
     */
    public String getConversationId(){return conversationId;}

    /**
     *
     * @param value Conversation Id of ebXML message
     */
    public void setConversationId(String value){conversationId = value;}
    
    /**
     *
     * @return Duplicate Elimination status of ebXML Message
     */
    public boolean getDuplicateElimination(){return duplicateElimination;}

    /**
     *
     * @param value Duplicate Elimination status of ebXML Message
     */
    public void setDuplicationElimination(boolean value){duplicateElimination = value;}
    
    /**
     * 
     * @return Whether an acknowledgment was requested for the message. 
     */
    public boolean ackRequested() { return ackrequested; }
    
    /**
     *
     * @return Service of ebXML Message
     */
    public String getService(){return service;}

    /**
     *
     * @param value Service of ebXML Message
     */
    public void setService(String value){service = value;}
    
    /**
     *
     * @return Interaction Id of ebXML Message
     */
    public String getInteractionId(){return interactionid;}

    /**
     *
     * @param value Interaction Id of ebXML Message
     */
    public void setInteractionId(String value){interactionid = value;}
    
    /**
     *
     * @return Message Id of ebXML Message
     */
    public String getMessageId(){return messageId;}

    /**
     *
     * @param value Message Id of ebXML Message
     */
    public void setMessageId(String value){messageId = value;}

    /**
     *
     * @return CPAID of ebXML Message
     */
    public String getCpaId(){return cpaid;}

    /**
     *
     * @param value CPAID of ebXML Message
     */
    public void setCpaId(String value){cpaid = value;}


    /**
     * Implementation of Attachment.serialise()
     * @return String representation of the message with substitutions made
     */
    @Override
    public String serialise() {
        if (serialisation != null) {
            return serialisation;
        }
        StringBuilder sb = new StringBuilder(ebxmlheadertemplate);
        substitute(sb,"__FROM_PARTY_KEY__", myPartyKey);
        substitute(sb,"__TO_PARTY_KEY__", toPartyKey);
        substitute(sb,"__CPAID__", cpaid);
        substitute(sb,"__CONVERSATION_ID__", (conversationId == null) ? messageId : conversationId);
        substitute(sb,"__SERVICE__", service);
        substitute(sb,"__INTERACTION_ID__", interactionid);
        substitute(sb,"__MESSAGE_ID__", messageId);
        timeStamp = ISO8601DATEFORMAT.format(new Date());
        substitute(sb,"__TIMESTAMP__", timeStamp);
        if (duplicateElimination) {
            substitute(sb,"__DUPLICATE_ELIMINATION__", DUPLICATEELIMINATIONELEMENT);
        } else {
            substitute(sb,"__DUPLICATE_ELIMINATION__", "");
        }
        if (ackrequested) {
            StringBuilder ar = new StringBuilder(ACKREQUESTEDELEMENT);
            substitute(ar,"__SOAP_ACTOR__", soapActor);
            substitute(sb,"__ACK_REQUESTED__", ar.toString());
        } else {
            substitute(sb,"__ACK_REQUESTED__", "");
        }
        if (syncreply) {
            substitute(sb,"__SYNC_REPLY__", SYNCREPLYELEMENT);
        } else {
            substitute(sb,"__SYNC_REPLY__", "");
        }
        substitute(sb,"__REFERENCES__", buildReferences());
        serialisation = sb.toString();
        return serialisation;
    }

    /**
     * Internal call to construct the references in the header manifest, from
     * the other attachments held in the EbXmlMessage
     */
    private String buildReferences() {
        StringBuilder sb = new StringBuilder(ebxml.getHL7Message().getEbxmlReference());
        if (ebxml.getAttachments() != null) {
            for(Attachment a: ebxml.getAttachments()) {
                    sb.append(a.getEbxmlReference());
            }
        }
        return sb.toString();
    }
}
