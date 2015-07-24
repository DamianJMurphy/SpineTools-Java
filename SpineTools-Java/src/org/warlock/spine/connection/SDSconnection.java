/*
  Copyright 2012 Damian Murphy <murff@warlock.org>

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
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.ldap.InitialLdapContext;
/**
 * Wrapper for an LDAP connection using the given <code>SpineSecurityContext</code>,
 * can be instantiated with the LDAP URL in the constructor, or set in system properties.
 * 
 * @author Damian Murphy <murff@warlock.org>
 */
public class SDSconnection {
   
    /**
     * Property name for setting the SDS URL in System.properties
     */
    public static final String SDSURL = "org.warlock.spine.sds.url";
    
    protected InitialLdapContext ldapContext = null;
    protected String sdsUrl = null;
    
    /**
     * Connect using the given URL
     * @param u LDAP URL to connect
     * @throws Exception
     */
    public SDSconnection(String u) 
            throws Exception
    {
        sdsUrl = u;
        //init();
    }
    
    /**
     * Connect using the URL contained in system property org.warlock.spine.sds.url
     * @throws Exception 
     */
    public SDSconnection() 
            throws Exception
    {
        sdsUrl = System.getProperty(SDSURL);
        if (sdsUrl == null) {
            throw new Exception("No SDS URL given");
        }
        //init();
    }
    
    /**
     * Return the LDAP connection so that operations can be made on it.
     * @return 
     */
    public DirContext getContext() 
            throws Exception
    { 
        if (ldapContext == null)
            init();
        return ldapContext; 
    }
    
    /**
     * Close the connection.
     * @throws Exception 
     */
    public void shutdown()
            throws Exception
    {
        if (ldapContext != null) {
            ldapContext.close();
        }
    }
    
    private void init()
            throws Exception 
    {
        @SuppressWarnings("UseOfObsoleteCollectionType")
        Hashtable<String,String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, sdsUrl);
        if (ConditionalCompilationControls.LDAPS && !ConditionalCompilationControls.OPENTEST) {
            env.put(Context.SECURITY_PROTOCOL, "ssl");
            env.put(Context.SECURITY_AUTHENTICATION, "none");
            if (ConditionalCompilationControls.LDAPOVERTLS && !ConditionalCompilationControls.OPENTEST) {
                env.put("java.naming.ldap.factory.socket", "org.warlock.spine.connection.SpineSecurityContext");
            }
        }
        ldapContext = new InitialLdapContext(env, null);
    }
    
}
