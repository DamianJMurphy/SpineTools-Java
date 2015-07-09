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
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;


    /**
     * Concrete subclass of Attachment for holding the "HL7 payload" of a Spine
     * message, and constructing wrappers. With no payload, this class
     * represents the "ITK Trunk" message which consists of minimal HL7
     * transmission and control act wrappers only to satisfy TMS' requirements
     * for a message to declare ASID and author details.
     */

public class SpineHL7Message extends Attachment {


    public static final String HL7V3XMLMIMETYPE = "application/xml; charset=UTF-8";
    public static final String SCHEMAELEMENT = "<eb:Schema eb:location=\"http://www.nhsia.nhs.uk/schemas/HL7-Message.xsd\" eb:version=\"1.0\"/>\r\n";
    public static final String DESCRIPTION = "HL7 payload";
    public static final String HL7PAYLOADELEMENT = "<hl7ebxml:Payload style=\"HL7\" encoding=\"XML\" version=\"3.0\"/>\r\n";
    public static final String HL7V3NS = "urn:hl7-org:v3";

    private static final String HL7WRAPPERTEMPLATE = "hl7_wrapper_template.txt";
    private static final String AUTHORTEMPLATE = "hl7_author_template.txt";

    private static final SimpleDateFormat DATETIME = new SimpleDateFormat("yyyyMMddHHmmss");

    private static final String INTERACTIONID = "__INTERACTION_ID__";
    private static final String MESSAGEID = "__MESSAGE_ID__";
    private static final String CREATIONTIME = "__CREATION_TIME__";
    private static final String TOASID = "__TO_ASID__";
    private static final String MYASID = "__MY_ASID__";
    private static final String SUBJECTSTART = "__SUBJECT_START_TAG__";
    private static final String SUBJECTEND = "__SUBJECT_END_TAG__";
    private static final String HL7PAYLOAD = "__HL7_PAYLOAD__";
    private static final String AUTHORUID = "__AUTHOR_UID__";
    private static final String AUTHORURP = "__AUTHOR_URP__";
    private static final String AUTHORROLE = "__AUTHOR_ROLE__";
    private static final String AUTHORELEMENT = "__AUTHOR_ELEMENT__";

    private String hl7v3Payload = null;
    private String interactionId = null;
    private String messageId = null;
    private boolean isQuery = false;

    private String myasid = null;
    private String toasid = null;
    private String authoruid = null;
    private String authorurp = null;
    private String authorrole = null;

    // Populated for received messages
    private String fromAsid = null;

    private static String wrapperTemplate = null;
    private static String authorTemplate = null;
    private static Exception bootException = null;
    private String serialisation = null;

    /**
     * Initialise a SpineHL7Message from a parsed XmlDocument containing the
     * payload.
     *
     * @param ia Service-qualified interaction id
     * @param h Parsed XmlDocument containing the HL7 payload
     * @throws java.lang.Exception
     */
    public SpineHL7Message(String ia, Document h)
            throws Exception
    {
        StringWriter sw = new StringWriter();
        StreamResult sr = new StreamResult(sw);
        Transformer tx = TransformerFactory.newInstance().newTransformer();
        tx.transform(new DOMSource(h), sr);
        init(ia, sw.toString());
    }

    /**
     * Initialise a SpineHL7Message from an unparsed String containing the
     * payload.
     *
     * @param ia Service-qualified interaction id
     * @param h String containing the HL7 payload
     */
    public SpineHL7Message(String ia, String h) {
        init(ia, h);
    }

    /**
     * Used in message receipt to construct a SpineHL7Message from the given
     * MIME part. This also extracts required details from the received message.
     *
     * @param m MIME part body
     * @throws java.lang.Exception
     */
    public SpineHL7Message(String m) 
            throws Exception
    {
        
        hl7v3Payload = stripMimeHeaders(m);
        setMimeType(HL7V3XMLMIMETYPE);
        serialisation = hl7v3Payload;
        int idstart = hl7v3Payload.indexOf("id");
        if (idstart == -1)
            throw new Exception("Malformed HL7v3 - no message id");
        idstart = hl7v3Payload.indexOf("root", idstart);
        if (idstart == -1)
            throw new Exception("Malformed HL7v3 - no message id (cannot find message id root)");
        idstart = hl7v3Payload.indexOf("\"", idstart);
        if (idstart == -1)
            throw new Exception("Malformed HL7v3 - no message id (cannot find start of message id root)");
        int idend = hl7v3Payload.indexOf("\"", idstart + 1);
        if (idend == -1)
            throw new Exception("Malformed HL7v3 - no message id (cannot find end of message id root)");
        messageId = hl7v3Payload.substring(idstart + 1, idend);

        idstart = hl7v3Payload.indexOf("interactionId");
        if (idstart == -1)
            throw new Exception("Malformed HL7v3 - no interaction id");
        idstart = hl7v3Payload.indexOf("extension", idstart);
        if (idstart == -1)
            throw new Exception("Malformed HL7v3 - no interaction id (cannot find interaction id extension)");
        idstart = hl7v3Payload.indexOf("\"", idstart);
        if (idstart == -1)
            throw new Exception("Malformed HL7v3 - no interaction id (cannot find start of interaction id extension)");
        idend = hl7v3Payload.indexOf("\"", idstart + 1);
        if (idend == -1)
            throw new Exception("Malformed HL7v3 - no interaction id (cannot find end of interaction id extension)");
        interactionId = hl7v3Payload.substring(idstart + 1, idend);        
        isQuery = (interactionId.charAt(0) == 'Q');
        
        idstart = hl7v3Payload.indexOf("communicationFunctionSnd");
        if (idstart == -1)
            throw new Exception("Malformed HL7v3 - no from ASID");
        idstart = hl7v3Payload.indexOf("extension", idstart);
        if (idstart == -1)
            throw new Exception("Malformed HL7v3 - no from ASID (cannot find device id extension)");
        idstart = hl7v3Payload.indexOf("\"", idstart);
        if (idstart == -1)
            throw new Exception("Malformed HL7v3 - no from ASID (cannot find start of device id root)");
        idend = hl7v3Payload.indexOf("\"", idstart + 1);
        if (idend == -1)
            throw new Exception("Malformed HL7v3 - no from ASID (cannot find end of device id root)");
        fromAsid = hl7v3Payload.substring(idstart + 1, idend);
        
        // IMPROVEMENT: Add a configuration item to (optionally) check that the "toAsid" is us.
    }

    /**
     * Internal call to load an embedded resource template and return it as a
     * String.
     */
    private String loadTemplate(String t) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader((org.warlock.spine.messaging.SpineHL7Message.class.getResourceAsStream(t))));
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (Exception e) {
            System.err.println("Fatal: Error reading control template: " + t);
            System.err.println(e.toString());
            return null;
        }
        return sb.toString();
    }

    private void init(String ia, String h) {
        interactionId = ia;
        isQuery = (interactionId.charAt(0) == 'Q');
        hl7v3Payload = h;
        setMimeType(HL7V3XMLMIMETYPE);
        description = DESCRIPTION;
        schema = SCHEMAELEMENT;
        messageId = UUID.randomUUID().toString().toUpperCase();
        synchronized (DESCRIPTION) {
            if (wrapperTemplate == null) {
                wrapperTemplate = loadTemplate(HL7WRAPPERTEMPLATE);
            }
            if (authorTemplate == null) {
                authorTemplate = loadTemplate(AUTHORTEMPLATE);
            }
        }
    }

    /**
     *
     * @return The message ID is returned
     */
    public String getMessageID() {return messageId;}

    /**
     * Does this use a "query" element, or a "subject" between the control act
     * wrapper and the payload ? We could probably infer this from the initial
     * letter of the interaction id, but that is a bit too much like
     * muck-and-magic even for HL7.
     *
     * @return Does this use a "query" element between the control act wrapper and the payload?
     */
    public boolean isQuery() {return isQuery;}

    /**
     *
     * @return The ASID of the message
     */
    public String getMyAsid() {return myasid;}
    
    /**
     *
     * @param value The ASID of the message
     */
    public void setMyAsid(String value) {myasid = value;}

    /**
     *
     * @return The from ASID of the message
     */
    public String getFromAsid() {return fromAsid;}

    /**
     *
     * @return The to ASID of the message
     */
    public String getToAsid() {return toasid;}

    /**
     *
     * @param value The to ASID of the message
     */
    public void setToAsid(String value) {toasid = value;}

    /**
     *
     * @return The Author User ID
     */
    public String getAuthorUid() {return authoruid;}

    /**
     *
     * @param value The Author User ID
     */
    public void setAuthorUid(String value) {authoruid = value;}

    /**
     *
     * @return The Author User Role Profile
     */
    public String getAuthorUrp() {return authorurp;}

    /**
     *
     * @param value The Author User Role Profile
     */
    public void setAuthorUrp(String value) {authorurp = value;}

    /**
     *
     * @return The Author Role
     */
    public String getAuthorRole() {return authorrole;}

    /**
     *
     * @param value The Author Role
     */
    public void setAuthorRole(String value) {authorrole = value;}

    /**
     * Override abstract getEbxmlReference() in Attachment, for making the EbXml
     * manifest.
     *
     * @return The EBXML Manifest
     */
    @Override
    public String getEbxmlReference() {
        StringBuilder sb = new StringBuilder(REFERENCE);
        substitute(sb, "__CONTENT_SCHEME__", "cid:");
        substitute(sb, "__CONTENT_ID__", contentid);
        StringBuilder rc = new StringBuilder();
        rc.append(SCHEMAELEMENT);
        rc.append(DESCRIPTIONELEMENT);
        substitute(rc, "__DESCRIPTION__", description);
        rc.append(HL7PAYLOADELEMENT);
        substitute(sb, "__REFERENCE_BODY__", rc.toString());
        return sb.toString();
    }

    /**
     * Override abstract serialise() in Attachment, for sending the message.
     *
     * @return String representation of the message with substitutions made
     */
    @Override
    public String serialise() {
        if (serialisation != null)
            return serialisation;
        StringBuilder sb = new StringBuilder(wrapperTemplate);
        substitute(sb, INTERACTIONID, interactionId);
        substitute(sb, MESSAGEID, messageId);
        substitute(sb, TOASID, toasid);
        substitute(sb, MYASID, myasid);
        substitute(sb, CREATIONTIME, DATETIME.format(new Date()));
        substitute(sb, AUTHORELEMENT, makeAuthorElement());
        if ((hl7v3Payload == null) || (hl7v3Payload.length() == 0)) {
            substitute(sb, SUBJECTSTART, "");
            substitute(sb, HL7PAYLOAD, "");
            substitute(sb, SUBJECTEND, "");
        } else {
            if (!isQuery) {
                substitute(sb, SUBJECTSTART, "<subject>");
            } else {
                substitute(sb, SUBJECTSTART, "");
            }
            substitute(sb, HL7PAYLOAD, stripHl7Xml());
            if (!isQuery) {
                substitute(sb, SUBJECTEND, "</subject>");
            } else {
                substitute(sb, SUBJECTEND, "");
            }
        }
        serialisation = sb.toString();
        return serialisation;
    }

    /**
     * The interaction is built as a String, so get rid of any unwanted XML
     * processing directives.
     */
    private String stripHl7Xml() {
        if (hl7v3Payload == null) {
            return "";
        }
        if (hl7v3Payload.startsWith("<?xml ")) {
            return hl7v3Payload.substring(hl7v3Payload.indexOf('>') + 1);
        }
        return hl7v3Payload;
    }

    private String makeAuthorElement() {
        if (authoruid == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(authorTemplate);
        substitute(sb, AUTHORUID, authoruid);
        substitute(sb, AUTHORURP, authorurp);
        substitute(sb, AUTHORROLE, authorrole);
        return sb.toString();
    }

    /**
     *
     * @return HL7 Payload
     */
    public String getHL7Payload() {
        return hl7v3Payload;
    }

}
