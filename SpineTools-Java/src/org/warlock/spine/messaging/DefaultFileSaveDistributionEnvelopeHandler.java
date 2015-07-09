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
import org.warlock.itk.distributionenvelope.DistributionEnvelope;
/**
 * DistributionEnvelope handler that writes the envelope and its content, to a file on disk.
 * This is the default behaviour of the MHS' ITK Trunk handler used where it has no other 
 * handler specifically registered against the received ITK service.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class DefaultFileSaveDistributionEnvelopeHandler 
    implements DistributionEnvelopeHandler
{
    /**
     * System property. Holds the path where received ITK Distribution Envelopes are written. Note
     * that if this property is not set, the system "user.dir" property is used.
     */
    private static final String SAVE_DIRECTORY = "org.warlock.spine.messaging.defaultdistributionenvelopehandler.filesavedirectory";
    
    /**
     * System property. Set to something beginning with "y" or "Y" to cause the fully-qualified
     * path name of the file, to be written to System.out. 
     */
    private static final String REPORT_FILENAME = "org.warlock.spine.messaging.defaultdistributionenvelopehandler.reportfilename";

    private File fileSaveDirectory = null;
    private boolean reportFilename = false;

    public DefaultFileSaveDistributionEnvelopeHandler()
            throws FileNotFoundException, IllegalArgumentException
    {
        String fsd = System.getProperty(SAVE_DIRECTORY);
        if (fsd == null) {
            fsd = System.getProperty("user.dir");
        }
        fileSaveDirectory = new File(fsd);
        if (!fileSaveDirectory.exists()) {
            throw new FileNotFoundException("Default ITK distribution envelope handler save location does not exist");
        }
        if (!fileSaveDirectory.isDirectory()) {
            throw new IllegalArgumentException("Default ITK distribution envelope handler save location must be a directory");
        }
        if (!fileSaveDirectory.isDirectory()) {
            throw new IllegalArgumentException("Default ITK distribution envelope handler save location must be writable");
        }       
        String rf = System.getProperty(REPORT_FILENAME);
        reportFilename = ((rf != null) && (rf.toLowerCase().startsWith("y")));            
    }
    
    /**
     * Parses the distribution envelope payloads, and writes the envelope plus contents to disk.
     * @param d
     * @throws Exception 
     */
    @Override
    public void handle(DistributionEnvelope d)
            throws Exception
    {
        // The initial parsing for the DistributionEnvlope doesn't extract
        // the payloads (because it is intended to be called by routers that
        // don't need to know about payloads). That will cause an exception
        // to be thrown when we try to write the DE to a string, so call
        // "parsePayloads()" here..
        //
        d.parsePayloads();
        String s = d.getService();
        s = s.replaceAll(":", "_");
        StringBuilder sb = new StringBuilder(s);
        sb.append("_");
        sb.append(getFileSafeMessageID(d.getTrackingId()));
        sb.append(".message");
        File outfile = new File(fileSaveDirectory, sb.toString());
        try (FileWriter fw = new FileWriter(outfile)) {
            fw.write(d.toString());
            
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
    
}
