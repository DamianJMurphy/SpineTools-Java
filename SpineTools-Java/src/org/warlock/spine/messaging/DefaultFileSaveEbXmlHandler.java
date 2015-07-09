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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;

/**
 * Default implementation of SpineEbXmlHandler that writes a received message to disk.
 * An instance of this handler is created by the Connection Manager and used where no
 * other has been explicitly mapped for a service/interaction.  It is provided partly
 * as an example, and partly as a simple means of integrating Spine ebXml messaging
 * with legacy systems.
 * 
 * Note that this class has no link to any original ebXml request that the MHS may have sent,
 * and does not attempt to correlate received and sent messages (hence it is
 * equally applicable to receiving asynchronous responses, and unsolicited messages).
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class DefaultFileSaveEbXmlHandler 
    implements SpineEbXmlHandler
{
    /**
     * System property. Holds the path where received messages are written. Note
     * that if this property is not set, the system "user.dir" property is used.
     */    
    private static final String SAVE_DIRECTORY = "org.warlock.spine.messaging.defaultebxmlhandler.filesavedirectory";
    
    /**
     * System property. Set to something beginning with "y" or "Y" to cause the fully-qualified
     * path name of the file, to be written to System.out. 
     */    
    private static final String REPORT_FILENAME = "org.warlock.spine.messaging.defaultebxmlhandler.reportfilename";

    private File fileSaveDirectory = null;
    private boolean reportFilename = false;
    
    public DefaultFileSaveEbXmlHandler() 
            throws FileNotFoundException, IllegalArgumentException
    {
        String fsd = System.getProperty(SAVE_DIRECTORY);
        if (fsd == null) {
            fsd = System.getProperty("user.dir");
        }
        fileSaveDirectory = new File(fsd);
        if (!fileSaveDirectory.exists()) {
            throw new FileNotFoundException("Default ebXml handler save location does not exist");
        }
        if (!fileSaveDirectory.isDirectory()) {
            throw new IllegalArgumentException("Default ebXml handler save location must be a directory");
        }
        if (!fileSaveDirectory.isDirectory()) {
            throw new IllegalArgumentException("Default ebXml handler save location must be writable");
        }       
        String rf = System.getProperty(REPORT_FILENAME);
        reportFilename = ((rf != null) && (rf.toLowerCase().startsWith("y")));
    }
    
    
    @Override
    public void handle(EbXmlMessage m) 
            throws Exception
    {
        StringBuilder sb = new StringBuilder(m.getHeader().getInteractionId());
        sb.append("_");
        sb.append(getFileSafeMessageID(m.getMessageId()));
        sb.append(".message");
        File outfile = new File(fileSaveDirectory, sb.toString());
        try (FileWriter fw = new FileWriter(outfile)) {
            fw.write(m.getHl7Payload());
            
            fw.flush();
        }
        
        if (reportFilename) {
            System.out.println(outfile.getCanonicalPath());
        }
    }
    
    private String getFileSafeMessageID(String s) 
    {
        if (s == null)
            return s;
        if (!s.contains(":"))
            return s;
        StringBuilder sb = new StringBuilder(s);
        int c = -1;
        while((c = sb.indexOf(":")) != -1) {
            sb.setCharAt(c, '_');
        }
        return sb.toString();
    }    

    
    private String getLastServiceURIElement(String s)
    {
        if (s == null)
            return s;
        return s.substring(s.indexOf('/') + 1);
    }
}
