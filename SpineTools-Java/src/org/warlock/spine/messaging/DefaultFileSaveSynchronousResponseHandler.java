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
 * SynchronousResponseHandler that writes the response to a file on disk. The ConnectionManager
 * will instantiate either a DefaultFileSaveSynchronousResponseHandler or a NullSynchronousResponseHandler
 * for use when no explicit handler is mapped against a request type.
 * 
 * Note that this class just saves the response, which is stored in the SpineSOAPRequest in any
 * case. Integration tasks that just need to get a response back from Spine should use the 
 * NullSynchronousResponseHandler or a subclass if it, unless they need to maintain a copy of the response (in which
 * case they should use this or a subclass).
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class DefaultFileSaveSynchronousResponseHandler 
    implements SynchronousResponseHandler
{
   /**
     * System property. Holds the path where responses are written. Note
     * that if this property is not set, the system "user.dir" property is used.
     */       
    private static final String SAVE_DIRECTORY = "org.warlock.spine.messaging.defaultsynchronousresponsehandler.filesavedirectory";
    
    /**
     * System property. Set to something beginning with "y" or "Y" to cause the fully-qualified
     * path name of the file, to be written to System.out. 
     */        
    private static final String REPORT_FILENAME = "org.warlock.spine.messaging.defaultsynchronousresponsehandler.reportfilename";

    private File fileSaveDirectory = null;
    private boolean reportFilename = false;
    
    public DefaultFileSaveSynchronousResponseHandler() 
        throws FileNotFoundException, IllegalArgumentException
    {
        String fsd = System.getProperty(SAVE_DIRECTORY);
        if (fsd == null) {
            fsd = System.getProperty("user.dir");
        }
        fileSaveDirectory = new File(fsd);
        if (!fileSaveDirectory.exists()) {
            throw new FileNotFoundException("Default synchronous response handler save location does not exist");
        }
        if (!fileSaveDirectory.isDirectory()) {
            throw new IllegalArgumentException("Default synchronous response handler save location must be a directory");
        }
        if (!fileSaveDirectory.isDirectory()) {
            throw new IllegalArgumentException("Default synchronous response handler save location must be writable");
        }       
        String rf = System.getProperty(REPORT_FILENAME);
        reportFilename = ((rf != null) && (rf.toLowerCase().startsWith("y")));    
    }
    
    @Override
    public void handle(SpineSOAPRequest r) 
            throws Exception
    {
        StringBuilder sb = new StringBuilder(getLastServiceURIElement(r.getSoapAction()));
        sb.append("_");
        sb.append(getFileSafeMessageID(getFileSafeMessageID(r.getMessageId())));
        sb.append(".message");
        File outfile = new File(fileSaveDirectory, sb.toString());
        try (FileWriter fw = new FileWriter(outfile)) {
            fw.write(r.getSynchronousResponse());
            
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
        if (s.contains("/")) 
            return s.substring(s.indexOf('/') + 1);
        return getFileSafeMessageID(s);
    }    
}
