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
package org.warlock.spine.messaging;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.UUID;
import org.warlock.spine.connection.ConnectionManager;
import org.warlock.spine.connection.SdsTransmissionDetails;
/**
 * Sendable implementation of a Spine synchronous request.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class SpineSOAPRequest 
    extends Sendable
{
    private static final String SOAPREQUESTTEMPLATE = "SpineSoapTemplate.txt";
    private static final String HTTPHEADER = "POST __CONTEXT_PATH__ HTTP/1.1\r\nHost: __HOST__\r\nSOAPAction: __SOAP_ACTION__\r\nContent-Length: __CONTENT_LENGTH__\r\nContent-Type: text/xml; charset=utf-8\r\nConnection: close\r\n\r\n";
    private SpineHL7Message hl7message = null;
    private SdsTransmissionDetails transmissionDetails = null;
    private String messageid = null;
    
    private static String template = null;
    private static Exception bootException = null;
    private static String myIp = null;
    private String myAsid = null;

   public SpineSOAPRequest(SdsTransmissionDetails c, SpineHL7Message m)
           throws Exception
   {
       if (bootException != null)
           throw bootException;
       type = SOAP;             
       ConnectionManager cm = ConnectionManager.getInstance();
       myAsid = (m.getMyAsid() == null) ?cm.getMyAsid() : m.getMyAsid();
       myIp = cm.getMyIP();
       messageid = UUID.randomUUID().toString().toUpperCase();
        synchronized (SOAPREQUESTTEMPLATE) {
            if (template == null) {
                StringBuilder sb = new StringBuilder();
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader((org.warlock.spine.messaging.SpineHL7Message.class.getResourceAsStream(SOAPREQUESTTEMPLATE))));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                        sb.append("\n");
                    }
                    template = sb.toString();
                } catch (Exception e) {
                    bootException = e;
                    throw e;
                }
            }
        }
        transmissionDetails = c;
        hl7message = m;
        String svcurl = cm.resolveUrl(c.getSvcIA());
        if (svcurl == null) {
            resolvedUrl = c.getUrl();
        } else {
            resolvedUrl = svcurl;
        }
   }
   
   @Override
   public void persist() throws java.io.IOException { }
   
   
    @Override
    public void write(OutputStream s) 
            throws Exception
    {
        StringBuilder sa = new StringBuilder(transmissionDetails.getService());
        sa.append("/");
        sa.append(transmissionDetails.getInteractionId());
        soapAction = sa.toString();
        
        StringBuilder sb = new StringBuilder(template);
        substitute(sb, "__MESSAGE_ID__", messageid);
        substitute(sb, "__SOAPACTION__", soapAction);
        substitute(sb, "__RESOLVED_URL__", resolvedUrl);
        substitute(sb, "__MY_IP__", myIp);
        substitute(sb, "__MY_ASID__", myAsid);
        substitute(sb, "__TO_ASID__", transmissionDetails.getAsids().get(0));
        substitute(sb, "__HL7_BODY__", getHL7body());
        String m = sb.toString();
        long l = (long)m.length();
        String httpHeader = makeHttpHeader(l);
        s.write(httpHeader.getBytes());
        s.flush();
        s.write(m.getBytes());
        s.flush();
    }
    
    private String getHL7body() {
        if (hl7message == null) {
            return "";
        }
        String s = hl7message.serialise();
        if (s.startsWith("<?xml ")) {
            return s.substring(s.indexOf('>') + 1);
        }
        return s;
    }
    
    
    private String makeHttpHeader(long l)
            throws Exception
    {
        StringBuilder sb = new StringBuilder(HTTPHEADER);
        URL u = new URL(resolvedUrl);
        substitute(sb, "__CONTEXT_PATH__", u.getPath());
        substitute(sb, "__HOST__", u.getHost());
        substitute(sb, "__SOAP_ACTION__", soapAction);
        substitute(sb, "__CONTENT_LENGTH__", Long.toString(l));
        return sb.toString();
    }
    
    @Override
    public void setResponse(Sendable r) {}
    
    @Override
    public Sendable getResponse() { return null; }
    
    @Override
    public String getMessageId() { return messageid; }
    
    @Override
    public void setMessageId(String s) { messageid = s; }
    
    @Override
    public String getResolvedUrl() { return resolvedUrl; }
    
    @Override
    public String getHl7Payload() { return hl7message.getHL7Payload(); }

    
}
