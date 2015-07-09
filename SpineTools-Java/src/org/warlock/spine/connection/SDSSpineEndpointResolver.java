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
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;
import javax.naming.directory.SearchControls;
import org.warlock.spine.logging.SpineToolsLogger;

/**
 * Wrapper class around an SDS connection plus cache, to resolve Spine endpoint queries.
 * Note that this depends on an anonymous bind to SDS, so is subject to the limitations
 * imposed by SDS on maximum return counts.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class SDSSpineEndpointResolver 
{
    // Internal constants for building LDAP queries.
    //
    private static final String[] ALL_ATTRIBUTES = null;
    private static final String UNIQUE_IDENTIFIER = "uniqueIdentifier";
    private static final String SERVICES_ROOT = "ou=services, o=nhs";
    private static final String MHSQUERY = "(&(objectclass=nhsMHS)(nhsMHSSvcIA=__SERVICE__)(nhsIDcode=__ORG__)__PARTYKEYFILTER__)";
    private static final String PKFILTER = "(nhsMhsPartyKey=__PK__)";
    private static final String ASQUERY = "(&(objectclass=nhsAS)(nhsASSvcIA=__SERVICE__)(nhsIDcode=__ORG__)(nhsMhsPartyKey=__PK__))";
    private static final String PARTYKEY = "nhsmhspartykey";
    private static final String UNIQUEIDENTIFIER = "uniqueidentifier";
    
    /**
     * REQUIRED System property. Directory on disk under which to write the SDS endpoint cache files.
     */
    public static final String CACHE_DIR_PROPERTY = "org.warlock.spine.sds.cachedir";
    
    /**
     * System property. Cache refresh period. This is currently unused, and may be removed in
     * a later version. Spine endpoint details of this sort don't change very often, and a
     * cache refresh may be most simply implemented by deleting a cache file. That could be
     * done manually (manual force refresh) or by an external, automated process. However it
     * works, cache refresh is currently delegated to something external to the MHS.
     */
    public static final String CACHE_REFRESH_PROPERTY = "org.warlock.spine.sds.cacherefresh";
    
    /**
     * REQUIRED System property. Local ASID.
     */
    public static final String MY_ASID_PROPERTY = "org.warlock.spine.sds.myasid";
    
    /**
     * REQUIRED System property. Local party key.
     */
    public static final String MY_PARTY_KEY_PROPERTY = "org.warlock.spine.sds.mypartykey";
    
    /**
     * System property. Optional location of a "URL resolver" file that maps service/interaction
     * data onto Spine service-level URLs. For those cases where an MHS will only talk to Spine
     * services (i.e. where the nhsMHSEndpointURL actually does contain the correct URL from the
     * sender's perspective) then this configuration may be omitted. However if it is omitted then
     * any Spine forwarded messaging will fail.
     */
    public static final String URL_RESOLVER_FILE_PROPERTY = "org.warlock.spine.sds.urlresolver";
    
    private static final int DEFAULT_CACHE_REFRESH = -1;
    
    private SDSconnection sdsconnection = null;
    private SdsCache cache = null;
    private String myAsid = null;
    private String myPartyKey = null;
    private HashMap<String,String> urlResolver = null;
    
    /**
     * Initialise with a new SDSconnection.
     * 
     * @throws Exception 
     */
    public SDSSpineEndpointResolver()
            throws Exception
    {
//        if (ConditionalCompilationControls.sdscacheonly) {
//            init();
//            return;
//        }
        if (ConditionalCompilationControls.TESTHARNESS) {
            if (ConditionalCompilationControls.cleartext) {
                init();
                return;
            }
        }
        sdsconnection = new SDSconnection();
        init();
    }
    
    /**
     * Initialise with the given SDSconnection.
     * 
     * @param c
     * @throws Exception 
     */
    public SDSSpineEndpointResolver(SDSconnection c)
            throws Exception
    {
        sdsconnection = c;
        init();
    }
    
    /**
     * Initialise with a new SDSconnection using the given LDAP URL.
     * 
     * @param u LDAP URL.
     * @throws Exception 
     */
    public SDSSpineEndpointResolver(String u)
            throws Exception
    {
        sdsconnection = new SDSconnection(u);
        init();
    }
    
    private void init()
            throws Exception
    {
        String cachedir = System.getProperty(CACHE_DIR_PROPERTY);
        String urlresolverfile = System.getProperty(URL_RESOLVER_FILE_PROPERTY);
        
        int refresh = DEFAULT_CACHE_REFRESH;
        try {
            refresh = Integer.parseInt(System.getProperty(CACHE_REFRESH_PROPERTY));
        }
        catch (NumberFormatException | NullPointerException nfe) {}
        myAsid = System.getProperty(MY_ASID_PROPERTY);
        myPartyKey = System.getProperty(MY_PARTY_KEY_PROPERTY);
        if (!isUsable(cachedir))
            throw new Exception("SDS cache directory property " + CACHE_DIR_PROPERTY + " not set");
        if (!isUsable(myAsid))
            throw new Exception("My ASID property " + MY_ASID_PROPERTY + " not set");
        if (!isUsable(myPartyKey))
            throw new Exception("My PartyKey property " + MY_PARTY_KEY_PROPERTY + " not set");
        
        cache = new SdsCache(cachedir, refresh);
        loadUrlResolver(urlresolverfile);
    }
    
    private void loadUrlResolver(String f)
    {
        if (!isUsable(f))
            return;
        urlResolver = new HashMap<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(f));
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || !isUsable(line))
                    continue;
                int tab = line.indexOf('\t');
                if (tab != -1) {
                    String s = line.substring(0, tab);
                    String u = line.substring(tab + 1);
                    urlResolver.put(s, u);
                }
            }
            br.close();
        }
        catch (java.io.IOException e)
        {
            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SDSSpineEndpointResolver.loadUrlResolver", e);
        }        
    }
    
    private boolean isUsable(String s)
    {
        return ((s != null) && (s.trim().length() > 0));
    }
    
    public String getMyAsid() { return myAsid; }
    public String getMyPartyKey() { return myPartyKey; }
  
    /**
     * Work around the design flaw in SDS that the nhsMhsEndpoint URL doesn't always contain
     * the URL that a sender needs to use. If there is a "resolved" URL, return it, if not
     * return null to indicate that the message should be sent to the endpoint URL given in
     * the SdsTransmissionDetails.
     * 
     * @param svcia Service-qualified interaction id for the message to be sent.
     * @return Resolved URL or null if the endpoint URL in SDS should be used.
     */    
    public String resolveUrl(String svcia)
    {
        if (urlResolver == null)
            return null;
        if (!urlResolver.containsKey(svcia))
            return null;
        return urlResolver.get(svcia);
    }
    
    /**
     * Resolves a list of SdsTransmissionDetails objects matching the given parameters. If there
     * is a local cache, this is read first and any match returned from there. Otherwise (or if
     * there is nomatch from the local cache) SDS is queried. Where details are retrieved from SDS
     * they are cached locally before being returned.
     * 
     * @param s "SvcIA" service-qualified interaction id, may not be null
     * @param o ODS code, may not be null
     * @param a ASID used as an additional filter if not null
     * @param p Party key used as an additional filter if not null
     *
     * @return List<SdsTransmissionDetails> of matching endpoint data
     */ 
    
    public ArrayList<SdsTransmissionDetails> getTransmissionDetails(String s, String o, String a, String p)
            throws IllegalArgumentException
    {
        if (s == null)
            throw new IllegalArgumentException("SvcIA may not be null");
        if (o == null)
            throw new IllegalArgumentException("ODS code may not be null");
         ArrayList<SdsTransmissionDetails> l = null;
        if (cache != null)
        {
            l = cache.getSdsTransmissionDetails(s, o, a, p);
        } 
        if (ConditionalCompilationControls.TESTHARNESS) {
            if (ConditionalCompilationControls.cleartext) {
                if (l == null)
                    return new ArrayList<>();
                else
                    return l;
            }
        }        
        if (l == null) 
            return ldapGetTransmissionDetails(s, o, a, p);
        else
            return l;
       
    }
    
    private ArrayList<SdsTransmissionDetails> ldapGetTransmissionDetails(String s, String o, String a, String p) 
    {
        // Two searches: one on nhsMHS to get all the entries for the service for the given organisation,
        // and then another on nhsAS to get all the AS entries (optionally filtering on ASID). Then build the
        // list of SdsTransmissionDetails, and add them to the cache.

        StringBuilder sbMhs = new StringBuilder(MHSQUERY);
        
        // replace the tags in MHSQUERY __SERVICE__ and __ORG__
        substitute(sbMhs, "__SERVICE__", s);
        substitute(sbMhs, "__ORG__", o);
        if (p != null) {
            StringBuilder pkf = new StringBuilder(PKFILTER);
            substitute(pkf, "__PK__", p);
            substitute(sbMhs, "__PARTYKEYFILTER__", pkf.toString());
        } else {
            substitute(sbMhs, "__PARTYKEYFILTER__", "");
        }
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(ALL_ATTRIBUTES);
        NamingEnumeration<SearchResult> results = null;
        ArrayList<SdsTransmissionDetails> output = null;
        try {
            results = sdsconnection.getContext().search(SERVICES_ROOT, sbMhs.toString(), controls);
            output = new ArrayList<SdsTransmissionDetails>();
            while (results.hasMore()) {
                SearchResult r = results.next();
                SdsTransmissionDetails sds = new SdsTransmissionDetails(r);
                output.add(sds);
                // "asid filtering" - and to find out if the LDAP context is reentrant :-)
                if (a == null) {
                    StringBuilder sbAs = new StringBuilder(ASQUERY);
                    substitute(sbAs, "__SERVICE__", s);
                    substitute(sbAs, "__ORG__", o);
                    substitute(sbAs, "__PK__", sds.getPartyKey());
                    NamingEnumeration<SearchResult> asidResult = null;
                    asidResult = sdsconnection.getContext().search(SERVICES_ROOT, sbAs.toString(), controls);                    
                    while (asidResult.hasMore()) {
                        SearchResult as = asidResult.next();
                        Attributes attrs = as.getAttributes();
                        String asid = (String)(attrs.get(UNIQUE_IDENTIFIER).get());
                        sds.addAsid(asid);                        
                    }
                } else {
                    ArrayList<String> asids = sds.getAsids();
                    if (!asids.contains(a))
                        asids.add(a);
                }
            }
        }
        catch (javax.naming.NamingException e) {
            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SDSSpineEndpointResolver.ldapGetTransmissionDetails.NamingException", e);            
            return null;
        }
        catch (Exception enull) {
            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.SDSSpineEndpointResolver.ldapGetTransmissionDetails.Exception", enull);
            return null;
        }
        // Should probably be threaded
        //
        if (cache != null) {
            for (SdsTransmissionDetails sds : output) {
                cache.cacheTransmissionDetail(sds);
            }
        }
        return output;
    }

    protected void substitute(StringBuilder sb, String tag, String content)
    {        
        int tagStart = sb.indexOf(tag);
        if (tagStart == -1) 
            return;
        int tagEnd = tagStart + tag.length();
        if (content != null) 
            sb.replace(tagStart, tagEnd, content);
        else
            sb.replace(tagStart, tagEnd, "");
    }
    
}
