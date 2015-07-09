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
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Properties;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
//import javax.net.ssl.SSLServerSocketFactory;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
/**
 * Class to handle the certificates and signing chains required for Spine messaging.
 * The <code>SpineSecurityContext</code> is also an implementation of a Socket Factory
 * which will issue SSL sockets secured with its certificates, and which are issued
 * with the SSL handshake already completed.
 * 
 * Spine certificates are issued without the signing sub-CA and root-CA certificates, 
 * only the signatures. It has been found during development of these classes that
 * the stock Java 1.6 runtime cannot resolve the signing sub- and root CA certificates
 * when they are present in the trust store only - irrespective of whether they are
 * in the "well known" store in $JRE_HOME/lib/security/cacerts or in a custom keystore,
 * with the result that the signing chain for the endpoint certificate cannot be retrieved.
 * 
 * The Java keystore has been found to be best populated in the following way:
 * <ul>
 * <li>Generate the key pair and certificate signing request (CSR) using OpenSSL</li>
 * <li>Have the CSR signed in the usual way</li>
 * <li>Concatenate the certificate, the Spine sub-CA and the Spine root-CA files in that order</li>
 * <li>Use OpenSSL to make a PKCS#12 file with the private key file, and the concatenated certificates as arguments</li>
 * <li>Use the Java keytool "importkeystore" function to make a Java keystore, with a source store type of "PKCS12"</li>
 * </ul>
 * 
 * Using the resultant keystore file allows the certificate chain to be resolved, and the
 * certificates at both the endpoint and the Spine side to pass the mutual authentication checks.
 * 
 * @author Damian Murphy <murff@warlock.org>
 */
public class SpineSecurityContext 
    extends SocketFactory
{    
    private static final String DEFAULT_JKS_PASSWORD = "changeit";
    private static SSLContext context = null;
    private static Properties properties = null;
    private static KeyStore keyStore = null;
    private static KeyStore trustStore = null;

    private boolean ready = false;
    
    /**
     * System property name for the certificate keystore file.
     */
    public static final String USESSLCONTEXT = "org.warlock.http.spine.certs";    
    
    /**
     * System property name for the truststore file.
     */
    public static final String USESSLTRUST = "org.warlock.http.spine.trust";
    
    /**
     * System property name for the certificate keystore password
     */
    public static final String SSLPASS = "org.warlock.http.spine.sslcontextpass";
    
    /**
     * System property name for the trust store password
     */
    public static final String TRUSTPASS = "org.warlock.http.spine.trustpass";
    
    /**
     * System property name for the SSL algorithm. May be left un-set, but must
     * be set if the local Java platform's default algorithm is NOT "SunX509".
     */
    public static final String SSLALGORITHM = "org.warlock.http.spine.sslalgorithm";
    
    // This is static because the security context will be called from the LdapContext
    // using the default constructpr, and we need to have the provider available to it.
    //
    private static PasswordProvider passwordProvider = null;
    
    /**
     * Constructor which will get configuration properties from System.properties
     * @throws Exception 
     */
    public SpineSecurityContext()
            throws Exception
    {
        properties = System.getProperties();
        init();
    }

    public SpineSecurityContext(PasswordProvider p)
            throws Exception
    {
        properties = System.getProperties();
        passwordProvider = p;
        init();
    }
    
    /**
     * Constructor which will get configuration properties from the given Properties
     * instance.
     * @param p
     * @throws Exception 
     */
    public SpineSecurityContext(Properties p)
            throws Exception
    {
        properties = p;
    }

    /**
     * Method to load the trust store, mainly for subclasses - applications should
     * call SpineSecurityContext.init() instead, which calls this, setupKeyStore()
     * and createContext() internally.
     * @throws Exception 
     */
    public void setupTrustStore()
            throws Exception
    {
        try {
            PasswordProvider pp = passwordProvider;
            if (pp == null)
                pp = ConnectionManager.getInstance().getPasswordProvider();
            String trst = properties.getProperty(USESSLTRUST);
            if (trst == null) {
                return;
            }
            String tp = pp.getPassword(TRUSTPASS);
            if (tp == null) {
                tp = properties.getProperty(TRUSTPASS);
                if (tp == null) tp = DEFAULT_JKS_PASSWORD;
            }
            trustStore = KeyStore.getInstance("jks");
            try (FileInputStream fis = new FileInputStream(trst)) {
                trustStore.load(fis, tp.toCharArray());
            }            
        }
        catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            System.err.println(e.toString());
            throw e;
        }
    }

    /**
     * Method to load the key store, mainly for subclasses - applications should
     * call SpineSecurityContext.init() instead, which calls this, setupTrustStore()
     * and createContext() internally.
     * @throws Exception 
     */

    public void setupKeyStore() 
            throws Exception
    {
        try {
            PasswordProvider pp = passwordProvider;
            if (pp == null)
                pp = ConnectionManager.getInstance().getPasswordProvider();
            String ksf = properties.getProperty(USESSLCONTEXT);
            String p = pp.getPassword(SSLPASS);
            if (p == null) {
                p = properties.getProperty(SSLPASS);
                if (p == null) p = "";
            }
            keyStore = KeyStore.getInstance("jks");
            try (FileInputStream fis = new FileInputStream(ksf)) {
                keyStore.load(fis, p.toCharArray());
            }
        }
        catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            System.err.println(e.toString());
            throw e;
        }
    }

    /**
     * Method to create the context, mainly for subclasses - applications should
     * call SpineSecurityContext.init() instead, which calls setupTrustStore()
     * and setupKeyStore() internally.
     * @throws Exception 
     */    
    public void createContext()
            throws Exception
    {
        try {
            PasswordProvider pp = passwordProvider;
            if (pp == null)
                pp = ConnectionManager.getInstance().getPasswordProvider();
            String alg = properties.getProperty(SSLALGORITHM);
            String p = pp.getPassword(SSLPASS);
            if (p == null) {
                p = properties.getProperty(SSLPASS);
                if (p == null) p = "";
            }
            KeyManagerFactory kmf = null;
            if (alg == null) {
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            } else {
                kmf = KeyManagerFactory.getInstance(alg);
            }
            kmf.init(keyStore, p.toCharArray());
            context = SSLContext.getInstance("TLS");            
            if (trustStore == null) {
                context.init(kmf.getKeyManagers(), null, new SecureRandom());            
            } else {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); 
                tmf.init(trustStore);
                context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());            
            }
            ready = true;
        }
        catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException | KeyManagementException e) {
            System.err.println(e.toString());
            throw e;
        }
    }
    
    /**
     * Check that everything was initialised correctly. Applications should call
     * this as a sanity check before trying to use the context to create sockets.
     * @return True if everything was set up correctly.
     */
    public boolean isReady() { return ready; }
    
    /**
     * Convenience method to load the key and trust stores, and to initialise
     * the context.
     * @throws Exception 
     */
    public final void init() 
            throws Exception
    {
        if (ConditionalCompilationControls.TESTHARNESS) {
            if (ConditionalCompilationControls.cleartext)
                return;
        }
        setupKeyStore();
        setupTrustStore();
        createContext();
    }
    
    /**
     * Get the client socket factory from the underlying SSL context. Applications
     * SHOULD use the implementations of the createSocket methods that are offered by the
     * SpineSecurityContext itself, directly.
     * @return Client socket factory.
     */
    public SSLSocketFactory getSocketFactory() { return context.getSocketFactory(); }
    
    /**
     * Get the server socket factory from the underlying SSL context.
     * @return Server socket factory
     */
    public ServerSocketFactory getServerSocketFactory() { 
        if (ConditionalCompilationControls.TESTHARNESS) {
            if (ConditionalCompilationControls.cleartext)
                return ServerSocketFactory.getDefault();
        }
        return context.getServerSocketFactory(); 
    }
    
    /**
     * Method to manually add a CA certificate to the internal trust store. 
     * @param certFile
     * @throws Exception 
     */
    public void addCACertificate(String certFile)
            throws Exception
    {
        X509Certificate c;
        try (FileInputStream fis = new FileInputStream(certFile)) {
            CertificateFactory cf = CertificateFactory.getInstance("X509");
            c = (X509Certificate)cf.generateCertificate(fis);
        }
        keyStore.setCertificateEntry(c.getSubjectDN().getName(), c);
    }
    
    @Override
    public java.net.Socket createSocket() 
            throws java.io.IOException
    {
        if (ConditionalCompilationControls.TESTHARNESS) {
            if (ConditionalCompilationControls.cleartext)
                return SocketFactory.getDefault().createSocket();
        }
        SSLSocket s = (SSLSocket)context.getSocketFactory().createSocket();
        s.startHandshake();
        return s;
    }
    
    @Override
    public java.net.Socket createSocket(String h, int p) 
            throws java.io.IOException, java.net.UnknownHostException
    {
        if (ConditionalCompilationControls.TESTHARNESS) {
            if (ConditionalCompilationControls.cleartext)
                return SocketFactory.getDefault().createSocket(h, p);
        }
        SSLSocket s = (SSLSocket)context.getSocketFactory().createSocket(h, p);
        s.startHandshake();
        return s;
    }

    @Override
    public java.net.Socket createSocket(String h, int p, java.net.InetAddress la, int lp) 
            throws java.io.IOException, java.net.UnknownHostException
    {
        if (ConditionalCompilationControls.TESTHARNESS) {
            if (ConditionalCompilationControls.cleartext)
                return SocketFactory.getDefault().createSocket(h, p, la, lp);
        }

        SSLSocket s = (SSLSocket)context.getSocketFactory().createSocket(h, p, la, lp);
        s.startHandshake();
        return s;
    }

    @Override
    public java.net.Socket createSocket(java.net.InetAddress a, int p) 
            throws java.io.IOException, java.net.UnknownHostException
    {
        if (ConditionalCompilationControls.TESTHARNESS) {
            if (ConditionalCompilationControls.cleartext)
                return SocketFactory.getDefault().createSocket(a, p);
        }
        SSLSocket s = (SSLSocket)context.getSocketFactory().createSocket(a, p);
        s.startHandshake();
        return s;
    }
   
    @Override
    public java.net.Socket createSocket(java.net.InetAddress a, int p, java.net.InetAddress la, int lp) 
            throws java.io.IOException, java.net.UnknownHostException
    {
        if (ConditionalCompilationControls.TESTHARNESS) {
            if (ConditionalCompilationControls.cleartext)
                return SocketFactory.getDefault().createSocket(a, p, la, lp);
        }
        
        SSLSocket s = (SSLSocket)context.getSocketFactory().createSocket(a, p, la, lp);
        s.startHandshake();
        return s;
    }
    
   public static javax.net.SocketFactory getDefault() {
       if (ConditionalCompilationControls.TESTHARNESS) {
           if (ConditionalCompilationControls.cleartext)
               return SocketFactory.getDefault();
       }
       try {
            return new SpineSecurityContext();
       }
       catch (Exception e) {
           e.printStackTrace();
           return null;
       }
   }
}
