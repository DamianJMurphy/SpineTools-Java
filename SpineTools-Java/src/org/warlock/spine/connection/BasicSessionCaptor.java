/*

 Copyright 2014 Health and Social Care Information Centre
 Solution Assurance damian.murphy@hscic.gov.uk

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
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.warlock.spine.messaging.Sendable;
/**
 * Simple implementation of a SessionCaptor that saves to a file based on message id.
 *
 * The file is controlled by two properties:
 * <code>org.warlock.spine.messaging.BasicSessionCaptor.directory</code> holds the directory name to which the files are written
 * <code>org.warlock.spine.messaging.BasicSessionCaptor.extension</code> is the file name extension to use (default .message)
 *
 * @author Damian Murphy damian.murphy@hscic.gov.uk
 */
public class BasicSessionCaptor 
    implements SessionCaptor
{
    private static final String SEPARATOR = "\n\n\n\n";
    private static final String DEFAULT_EXTENSION = ".message";
    private static final String DIRECTORYPROPERTY = "org.warlock.spine.messaging.BasicSessionCaptor.directory";
    private static final String EXTENSIONPROPERTY = "org.warlock.spine.messaging.BasicSessionCaptor.extension";
    
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
    
    private String directory = null;
    private String extension = DEFAULT_EXTENSION;
    
    private static byte[] separator = null;
    
    public BasicSessionCaptor() 
    {
        separator = SEPARATOR.getBytes();
        String p = null;
        
        p = System.getProperty(EXTENSIONPROPERTY);
        if (p != null)
            extension = p;
        
        p = System.getProperty(DIRECTORYPROPERTY);
        if (p != null)
            directory = p;
        else
            p = System.getProperty("user.dir");
    }
    
    @Override
    public void capture(Sendable s) {
        StringBuilder filename = new StringBuilder(s.getMessageId());
        filename.append("_");
        filename.append(FORMAT.format(new Date()));
        filename.append(extension);
        
        try {
            File f = new File(directory, filename.toString());
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(s.getOnTheWireRequest());
                fos.flush();
                fos.write(separator);
                fos.flush();
                fos.write(s.getOnTheWireResponse());
                fos.flush();
            }
        }
        catch(Exception e) {
            System.err.println("Exception capturing message " + s.getMessageId() + " : " + e.toString());
        }
    }
}
