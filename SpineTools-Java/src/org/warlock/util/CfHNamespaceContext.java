/*
 * CfHNamespaceContext.java
 *
 * Created on 03 April 2006, 11:03
 *
 *  14/06/2006 BIYA add comments
 *  22/02/2011 DAMU Added namespaces for XDR (SOAP 1.2, XOP, IHE XDS-B, EBXML LifeCycle and Registry RIM)
 */

package org.warlock.util;

/**
 *  A Singleton class for CfHNamespaceContext.
 */
public class CfHNamespaceContext 
{
    private static XMLNamespaceContext context;
    public static String WARLOCK_TOOLS_NAMESPACE = "urn:warlock-org:cfh:message-tools";
    public static String HL7NAMESPACE = "urn:hl7-org:v3";
    public static String SOAPENVNAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/";
    public static String EBXMLNAMESPACE = "http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd";
    public static String WSANAMESPACE = "http://www.w3.org/2005/08/addressing";
    public static String UIMNAMESPACE = "http://spine.nhs.uk/spine-servicev1.0/uim";
    public static String SPMLNAMESPACE = "urn:oasis:names:tc:SPML:2:0";
    public static String SPMLSEARCHNAMESPACE = "urn:oasis:names:tc:SPML:2:0:search";
    public static String NASPNAMESPACE = "http://spine.nhs.uk/spine-servicev1.0/";
    public static String DSNAMESPACE = "http://www.w3.org/2000/09/xmldsig#";
    public static String HL7V2NAMESPACE = "urn:hl7-org:v2xml";
    public static String TEST = "urn:warlock-org:test";
    public static String ITK = "urn:nhs-itk:ns:201005";
    public static String SECEXT = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    public static String SECUTIL = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    public static String WSSECURITY = "http://docs.oasis-open.org/wss/oasis-wss-wssecurity-secext-1.1.xsd";
    public static String NPFITLC = "NPFIT:HL7:Localisation";

    
    // Additions for XDR adapter
    // DJM: February 2011
    // Compatible with previous versions.

    public static String SOAP12 = "http://www.w3.org/2003/05/soap-envelope";
    public static String IHE_XDS_B = "urn:ihe:iti:xds-b:2007";
    public static String EB_REG_LCM = "urn:oasis:names:tc:ebxml-regrep:xsd:lcm:3.0";
    public static String EB_REG_RIM = "urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0";
    public static String XOP = "http://www.w3.org/2004/08/xop/include";

    static {
        if (context == null) {
            context = new XMLNamespaceContext();
            context.declarePrefix("hl7", HL7NAMESPACE);
            context.declarePrefix("w", WARLOCK_TOOLS_NAMESPACE);        
            context.declarePrefix("SOAP", SOAPENVNAMESPACE);
            context.declarePrefix("soap", SOAPENVNAMESPACE);
            context.declarePrefix("SOAP-ENV", SOAPENVNAMESPACE);
            context.declarePrefix("eb", EBXMLNAMESPACE);
            context.declarePrefix("wsa", WSANAMESPACE);
            context.declarePrefix("uim", UIMNAMESPACE);
            context.declarePrefix("spml", SPMLNAMESPACE);
            context.declarePrefix("spmlsearch", SPMLSEARCHNAMESPACE);
            context.declarePrefix("nasp", NASPNAMESPACE);
            context.declarePrefix("ds", DSNAMESPACE);
            context.declarePrefix("dsig", DSNAMESPACE);
            context.declarePrefix("hl7v2", HL7V2NAMESPACE);
            context.declarePrefix("test", TEST);
            context.declarePrefix("wsse", SECEXT);
            context.declarePrefix("wsu", SECUTIL);
            context.declarePrefix("wss", WSSECURITY);
            context.declarePrefix("itk", ITK);
            context.declarePrefix("npfitlc", NPFITLC);

            // Additions for XDR adapter
            // DJM: February 2011
            // Compatible with previous versions.
            
            context.declarePrefix("soap12", SOAP12);
            context.declarePrefix("ihexdsb", IHE_XDS_B);
            context.declarePrefix("eblcm", EB_REG_LCM);
            context.declarePrefix("ebrim", EB_REG_RIM);
            context.declarePrefix("xop", XOP);
        }
    }
    
    /**
     * Creates a new instance of HL7NamespaceContextFactory
     */
    private CfHNamespaceContext() {}
           
    /**
    *  return the single instance of this class.
    */
    public synchronized static XMLNamespaceContext getXMLNamespaceContext() { 
        if (context == null) {
            context = new XMLNamespaceContext();
            context.declarePrefix("hl7", HL7NAMESPACE);
            context.declarePrefix("w", WARLOCK_TOOLS_NAMESPACE);        
            context.declarePrefix("SOAP", SOAPENVNAMESPACE);
            context.declarePrefix("soap", SOAPENVNAMESPACE);
            context.declarePrefix("eb", EBXMLNAMESPACE);
            context.declarePrefix("wsa", WSANAMESPACE);
            context.declarePrefix("uim", UIMNAMESPACE);
            context.declarePrefix("spml", SPMLNAMESPACE);
            context.declarePrefix("spmlsearch", SPMLSEARCHNAMESPACE);
            context.declarePrefix("nasp", NASPNAMESPACE);
            context.declarePrefix("ds", DSNAMESPACE);
            context.declarePrefix("dsig", DSNAMESPACE);
            context.declarePrefix("hl7v2", HL7V2NAMESPACE);
            context.declarePrefix("test", TEST);
            context.declarePrefix("wsse", SECEXT);
            context.declarePrefix("wsu", SECUTIL);
            context.declarePrefix("wss", WSSECURITY);
            context.declarePrefix("itk", ITK);
            context.declarePrefix("npfitlc", NPFITLC);
            
            // Additions for XDR adapter
            // DJM: February 2011
            // Compatible with previous versions.

            context.declarePrefix("soap12", SOAP12);
            context.declarePrefix("ihexdsb", IHE_XDS_B);
            context.declarePrefix("eblcm", EB_REG_LCM);
            context.declarePrefix("ebrim", EB_REG_RIM);
            context.declarePrefix("xop", XOP);

        }
        return context; 
    }
    
}
