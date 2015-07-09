/*
 Copyright 2013 Health and Social Care Information Centre,
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
import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

/**
 * Abstract class representing a MIME part of a Spine EbXml message. This
 * handles serialisation of the MIME part, plus support for the EbXmlHeader
 * constructing the Manifest element.
 */
public abstract class Attachment {
    // Note: Make "inline" and "external" binary attachment concrete subclasses

    public static String CONTENTIDHEADER = "\r\nContent-Id: <";
    public static String CONTENTTYPEHEADER = ">\r\nContent-Type: ";
    public static String CTEHEADER = "\r\nContent-Transfer-Encoding: 8bit\r\n\r\n";
    protected static String REFERENCE = "<eb:Reference xlink:href=\"__CONTENT_SCHEME____CONTENT_ID__\">\r\n__REFERENCE_BODY__\r\n</eb:Reference>\r\n";
    protected static String DESCRIPTIONELEMENT = "<eb:Description xml:lang=\"en\">__DESCRIPTION__</eb:Description>\r\n";
    protected String mimetype = null;
    protected String contentid = UUID.randomUUID().toString();
    protected String description = null;
    protected String headerserialisation = null;

    protected String schema = null;

    /**
     *
     * @param m MIME Type
     */
    protected void setMimeType(String m) {
        mimetype = m;
    }

    /**
     *
     * @return The EBXML Manifest
     */
    public abstract String getEbxmlReference();

    /**
     *
     * @return String representation of the message with substitutions made
     */
    public abstract String serialise();

    /**
     * Compiled regular expression for extracting the MIME type from the content
     * type field of an attachment's MIME headers.
     */
    private Pattern mimeTypeExtractor = Pattern.compile("content-type: ([^\r\n]*)", Pattern.CASE_INSENSITIVE);

    /**
     * Retrieve the MIME type of a received attachment.
     *
     * @param s String containing MIME Type/content-type header
     * @return String representation of content-type
     * @throws java.lang.Exception
     */
    protected String getMimeType(String s) 
            throws Exception
    {
        // We get the attachment plus MIME headers,
        Matcher m = mimeTypeExtractor.matcher(s);
        if (!m.matches()) {
            throw new Exception("Invalid attachment - content-type not set");
        }
        mimetype = m.group(0);
        return mimetype;
    }

    /**
     * Strips the MIME headers and returns the rest of the String after the
     * "blank line" header delimiter.
     *
     * @param s The full MIME part including headers
     * @return The MIME part without the headers
     * @throws java.lang.Exception
     */
    protected String stripMimeHeaders(String s) 
        throws Exception
    {
        int bodyStart = s.indexOf("\r\n\r\n");
        if (bodyStart == -1) {
                // This is technically wrong, but we'll try it anyway
            //
            bodyStart = s.indexOf("\n\n");
            if (bodyStart == -1) {
                throw new Exception("Invalid MIME attachment - no header/body delimiter");
            }
        }
        headerserialisation = s.substring(0, bodyStart);
        String r = s.substring(bodyStart).trim();
        return r;
    }

    /**
     * Parse the received String as an Xml document.
     *
     * @param s XML as a String 
     * @return XmlDocument of the parsed MIME part
     * @throws java.lang.Exception
     */
    protected Document parseReceivedXml(String s) 
        throws Exception
    {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            String body = stripMimeHeaders(s);
            ByteArrayInputStream is = new ByteArrayInputStream(body.getBytes());
            Document doc = db.parse(is);
            return doc;
    }

    /**
     * For transmission, constructs a MIME part header for the attachment.
     * @return 
     */
    public String makeMimeHeader() {
        if (headerserialisation != null)
            return headerserialisation;
        StringBuilder sb = new StringBuilder(CONTENTIDHEADER);
        sb.append(contentid);
        sb.append(CONTENTTYPEHEADER);
        sb.append(mimetype);
        sb.append(CTEHEADER);
        headerserialisation = sb.toString();
        return headerserialisation;
    }

    /**
     *
     * @return Content Id
     */
    public String getContentId() {
        return contentid;
    }

    /**
     *
     * @return Description
     */
    public String getDescription() {
        return description;
    }

    /**
     *
     * @param value Description
     */
    public void setDescription(String value) {
        description = value;
    }

    /**
     *
     * @return Schema
     */
    public String getSchema() {
        return schema;
    }

    /**
     *
     * @param value Schema
     */
    public void setSchema(String value) {
        schema = value;
    }

    /**
     *
     * @return MIME Type
     */
    public String getMimeType() {
        return mimetype;
    }

    /**
     * A method to be used by any subclasses for substituting passed values into a 
     * StringBuilder object using substitution tags
     * @param sb StringBuilder representation of message to have substitutions
     * @param tag Substitution tags to be searched for and replaced
     * @param content Content which to substitute into the tags
     * @return Boolean response indicating if any substitutions have been made
     */
    protected boolean substitute(StringBuilder sb, String tag, String content) {
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
