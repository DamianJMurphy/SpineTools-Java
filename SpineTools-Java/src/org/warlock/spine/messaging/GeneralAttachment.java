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
import org.apache.commons.codec.binary.Base64;
/**
 * Note that the current "implementation" of this class is a placeholder.
 *
 * Class to represent a "general" binary attachment to a Spine EbXmlMessage
 */
public class GeneralAttachment extends Attachment {

    private String body = null;

    /**
     * Note that the current "implementation" of this class is a placeholder.
     * Class to represent a "general" binary attachment to a Spine EbXmlMessage
     *
     * @param m Received Message
     * @throws java.lang.Exception
     */
    public GeneralAttachment(String m) throws Exception {
        // For building from a received message
        String type = getMimeType(m);
        body = stripMimeHeaders(m);
    }

    @Override
    public String getEbxmlReference() {
        StringBuilder sb = new StringBuilder(REFERENCE);
        substitute(sb, "__CONTENT_SCHEME__", "cid:");
        substitute(sb, "__CONTENT_ID__", contentid);
        StringBuilder rc = new StringBuilder(DESCRIPTIONELEMENT);
        substitute(rc, "__DESCRIPTION__", description);
        substitute(sb, "__REFERENCE_BODY__", rc.toString());
        return sb.toString();
    }

    public void setBody(String s) { body = s; }
    public void setBody(byte[] b)
    {
        Base64 b64 = new Base64();
        byte[] enc = b64.encode(b);
        body = new String(enc);
    }
    
    @Override
    public String serialise() {
        return body;
    }

}
