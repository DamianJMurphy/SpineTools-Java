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
import java.util.ArrayList;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;
/**
 *
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class SdsTransmissionDetails {

    public static final int NOT_SET = -1;
    public static final int SINGLEVALUE = 0;
 
    // LDAP attributes we need to extract
    //
    public static final String LDAP_NHSIDCODE = "nhsidcode";
    public static final String LDAP_PARTYKEY = "nhsmhspartykey";
    public static final String LDAP_CPAID = "nhsmhscpaid";
    public static final String LDAP_INTERACTIONID = "nhsmhsin";
    public static final String LDAP_SVCIA = "nhsmhssvcia";
    public static final String LDAP_SVCNAME = "nhsmhssn";
    public static final String LDAP_ACKREQ = "nhsmhsackrequested";
    public static final String LDAP_SYNCREPLY = "nhsmhssyncreplymode";
    public static final String LDAP_SOAPACTOR = "nhsmhsactor";
    public static final String LDAP_DUPELIM = "nhsmhsduplicateelimination";
    public static final String LDAP_FQDN = "nhsmhsfqdn";
    public static final String LDAP_RETRIES = "nhsmhsretries"; 
    public static final String LDAP_RETRYINTERVAL = "nhsmhsretryinterval";
    public static final String LDAP_PERSISTDURATION = "nhsmhspersistduration";
    public static final String LDAP_ENDPOINT = "nhsmhsendpoint";

    // JSON member names
    //
    public static final String NHSIDCODE = "Org";
    public static final String PARTYKEY = "PartyKey";
    public static final String CPAID = "CPAid";
    public static final String INTERACTIONID = "InteractionId";
    public static final String SVCIA = "SvcIA";
    public static final String SVCNAME = "Service";
    public static final String ACKREQ = "AckRequested";
    public static final String SYNCREPLY = "SyncReply";
    public static final String SOAPACTOR = "SoapActor";
    public static final String DUPELIM = "DuplicateElimination";
    public static final String FQDN = "FQDN";
    public static final String RETRIES = "Retries"; 
    public static final String RETRYINTERVAL = "RetryInterval";
    public static final String PERSISTDURATION = "PersistDuration";
    public static final String ENDPOINT = "Url";
    public static final String ISSYNCHRONOUS = "IsSynchronous";
    
    public static final String ASIDS = "Asid";
    
    private String service = null;
    private String interactionid = null;
    private String svcia = null;
    private String orgcode = null;
    private String soapactor = null;
    private String ackrequested = null;
    private String duplicateelimination = null;
    private String syncreply = null;
    private String cpaid = null;
    private String partykey = null;
    @SuppressWarnings("FieldMayBeFinal")
    private ArrayList<String> asid = new ArrayList<>();
    private String url = null;
    private int retries = NOT_SET;
    private int retryinterval = NOT_SET;
    private int persistduration = NOT_SET;
    
    SdsTransmissionDetails() {}
  
// NOT used. Remove in later version.
//
//    public SdsTransmissionDetails(String org, String svc, String id) {
//        orgcode = org;
//        service = svc;
//        interactionid = id;
//        svcia = svc + ":" + id;
//    }
        
    /**
     * Called by the SDS LDAP interface to construct an SdsTransmissionDetails 
     * instance from the given LDAP search result.
     * 
     * @param r Search result
     * @throws javax.naming.NamingException 
     */
    public SdsTransmissionDetails(SearchResult r) 
            throws javax.naming.NamingException
    {
        Attributes attrs = r.getAttributes();
        // NamingEnumeration<String> attrIds = attrs.getIDs();
        orgcode = getAttributeValue(attrs, LDAP_NHSIDCODE);
        partykey = getAttributeValue(attrs, LDAP_PARTYKEY);
        cpaid = getAttributeValue(attrs, LDAP_CPAID);
        interactionid = getAttributeValue(attrs, LDAP_INTERACTIONID);
        svcia = getAttributeValue(attrs, LDAP_SVCIA);
        service = getAttributeValue(attrs, LDAP_SVCNAME);
        ackrequested = getAttributeValue(attrs, LDAP_ACKREQ);
        syncreply = getAttributeValue(attrs, LDAP_SYNCREPLY);
        soapactor = getAttributeValue(attrs, LDAP_SOAPACTOR);
        duplicateelimination = getAttributeValue(attrs, LDAP_DUPELIM);
        url = getAttributeValue(attrs, LDAP_ENDPOINT);
        
        retries = intValue(attrs, LDAP_RETRIES);
        retryinterval = iso8601DurationToSeconds(getAttributeValue(attrs, LDAP_RETRYINTERVAL));
        persistduration = iso8601DurationToSeconds(getAttributeValue(attrs, LDAP_PERSISTDURATION));        
    }

    /**
     * "Safe" attribute value extractor. Return empty strings for missing or
     * undefined values to avoid null pointer exceptions in code reading the contents
     * if LDAP search results.
     * 
     * @param attrs Attribute set from a SearchResult
     * @param name Attribute name to extract
     * @return String form of the attribute value, or an empty string if the value is not found.
     * @throws javax.naming.NamingException 
     */
    private String getAttributeValue(Attributes attrs, String name) 
        throws javax.naming.NamingException
    {
        if (name == null)
            return "";
        Attribute a = attrs.get(name);
        if (a == null)
            return "";
        String s = (String)(a.get());
        if (s == null)
            return "";
        return s;
    }
    
    void setOrgCode(String s) { orgcode  = s; }
    void setPartyKey(String s) { partykey = s; }
    void setCPAid(String s) { cpaid = s; }
    void setInteractionId(String s) { interactionid = s; }
    void setSvcIA(String s) { svcia = s; }
    void setService(String s) { service = s; }
    void setSyncReply(String s) { syncreply = s; }
    void setSoapActor(String s) { soapactor = s; }
    void setAckRequested(String s) { ackrequested = s; }
    void setDuplicateElimination(String s) { duplicateelimination = s; }
    void setUrl(String s) { url = s; }
    
    void addAsid(String s) { asid.add(s); }
    
    void setRetryInterval(int i) { retryinterval = i; }
    void setRetries(int i) { retries = i; }
    void setPersistDuration(int i) { persistduration = i; }    
    
    public String getUrl() { return url;}
    public String getOrgCode() { return orgcode; }
    public String getPartyKey() { return partykey; }
    public String getCPAid() { return cpaid; }
    public String getInteractionId() { return interactionid; }
    public String getSvcIA() { return svcia; }
    public String getService() { return service; }
    public String getSyncReply() { return syncreply; }
    public String getSoapActor() { return soapactor; }
    public String getAckRequested() { return ackrequested; }
    public String getDuplicateElimination() { return (duplicateelimination == null) ? "" : duplicateelimination; }
        
    public ArrayList<String> getAsids() { return asid; }
    
    public int getRetryInterval() { return retryinterval; }
    public int getRetries() { return retries; }
    public int getPersistDuration() { return persistduration; }
        
    public boolean isSynchronous() { 
        if ((getDuplicateElimination().toLowerCase()).contentEquals("always"))
            return false;
        return (((syncreply == null) || (syncreply.toLowerCase().trim().contentEquals("none"))));
    }
    
    /**
     * "Safe" attribute value extractor for integers.
     * 
     * @param attrs Attribute set
     * @param name Name of the attribute to extract
     * @return Integer value of the attribute, or -1 if not found.
     * @throws javax.naming.NamingException 
     */
    private int intValue(Attributes attrs, String name)
            throws javax.naming.NamingException
    {
        if (name == null)
            return -1;
        Attribute a = attrs.get(name);
        if (a == null)
            return -1;
        String s = (String)a.get();
        if (s == null)
            return -1;
        try {            
            return Integer.parseInt(s);
        }
        catch (java.lang.NumberFormatException e) {
            return -1;
        }
    }
    
    /**
     * Convert ISO 8601 duration (interval-of-time) format, to seconds. The format is
     * described at http://en.wikipedia.org/wiki/ISO_8601#Durations (or you can pay ISO
     * a stupid amount of money for the same thing). The SDS durations "retryInterval"
     * and "persistDuration" are presented in this format.
     * 
     * @param d ISO 8601 duration format
     * @return Equivalent in seconds
     */
    public static int iso8601DurationToSeconds(String d)
        {
            int seconds = 0;
            int multiplier = 1;
            for (int i = d.length() - 1; i > -1; i--)
            {
                char c = d.charAt(i);
                if (Character.isJavaIdentifierStart(c))
                {
                    switch (c)
                    {
                        case 'S':
                            multiplier = 1;
                            break;
                        case 'M':
                            multiplier = 60;
                            break;
                        case 'H':
                            multiplier = 3600;
                            break;
                        case 'T':
                            return seconds;
                    }
                }
                else
                {
                    seconds += (c - '0') * multiplier;
                    multiplier *= 10;
                }
            }
            return seconds;
        }    
}
