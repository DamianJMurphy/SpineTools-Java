/*
Copyright 2014 Health and Social Care Information Centre,
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
import org.warlock.spine.connection.ConnectionManager;
import java.io.OutputStream;
import java.net.URL;
import org.warlock.spine.connection.ConditionalCompilationControls;
import org.warlock.spine.logging.SpineToolsLogger;
    /**
     * Class to wrap an ebXml MessageAcknowledgment, as a concrete subclass of Sendable so it can
     * be returned asynchronously.
     * 
     * The ebXml MessageAcknowledgment body is the same whether it is returned synchronously (e.g.
     * for SPine-reliable or "forward reliable" messaging) or asynchronously (for "end-party reliable"
     * messaging). So it is created by the ConnectionManager. Where the acknowledgment is returned
     * synchronously it is just written to the already-open stream. Asynchronous acknowledgements are
     * constructed the same way, but then wrapped in an instance of this class so that they can
     * be passed to the ConnectionManager.send() method.
     */ 
public class EbXmlAcknowledgment 
    extends Sendable
{
    public static final String ACK_HTTP_HEADER = "POST /reliablemessaging/intermediary HTTP/1.1\r\nHost: __HOST__\r\nContent-Length: __CONTENT_LENGTH__\r\nConnection: close\r\nContent-Type: text/xml\r\nSOAPaction: urn:urn:oasis:names:tc:ebxml-msg:service/Acknowledgment\r\n\r\n";
    public static final String ACKSERVICE = "urn:oasis:names:tc:ebxml-msg:service:Acknowledgment";
    
    private String ack = null;

    
    public EbXmlAcknowledgment(String a) {
        ack = a;
        resolvedUrl = ConnectionManager.getInstance().resolveUrl(ACKSERVICE);
    }
    
    @Override
    public void persist() throws java.io.IOException {  }
    
    @Override
    public void write(OutputStream s) 
            throws Exception
    {         
        if (resolvedUrl == null)
            return;
        
        URL url = new URL(resolvedUrl);
        StringBuilder sb = new StringBuilder(ACK_HTTP_HEADER);
        int i = sb.indexOf("__HOST__");
        sb.replace(i, i + "__HOST__".length(), url.getHost());
        i = sb.indexOf("__CONTENT_LENGTH__");
        sb.replace(i, i + "__CONTENT_LENGTH__".length(), Integer.toString(ack.length()));
        sb.append(ack);
        s.write(sb.toString().getBytes());
        if(ConditionalCompilationControls.TESTHARNESS){
                if(ConditionalCompilationControls.otwMessageLogging){
                    SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SpineMessageHandler.message", "\r\nON THE WIRE OUTBOUND: \r\n\r\n"+ sb.toString());
                }
            }
            
    }

          /**
         * Static method for interrogating a received EbXmlAcknowledgment to determine the message
         * id that it is acking.
         * 
         * @param msg Received acknowledgment body
         * @return Extracted message id
         */ 
        public static String getAckedMessageId(String msg)
        {
            if (msg == null)
                return null;
            int start = msg.indexOf("RefToMessageId>");
            if (start == -1)
                return null;
            start += "RefToMessageId>".length();
            int end = msg.indexOf("<", start);
            if (end == -1)
                return null;
            return msg.substring(start, end);
        }
    
    @Override
    public void setResponse(Sendable r) {}
    
    @Override
    public Sendable getResponse() { return null; }
    
    @Override
    public String getMessageId() { return null; }
    
    @Override
    public void setMessageId(String s) {}
    
    @Override
    public String getResolvedUrl() { return resolvedUrl; }
    
    @Override
    public String getHl7Payload() { return null; }
}
