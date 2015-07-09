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
import org.warlock.itk.distributionenvelope.DistributionEnvelope;
import java.util.HashMap;
/**
 * Implementation of the SpineEbXmlHandler interface, specifically for the ITK Trunk
 * message. The Connection Manager automatically makes an instance of this class and
 * sets it as the handler for ITK Trunk messages.
 * 
 * The class implements its own handler mechanism for processing received ITK distribution
 * envelopes, that is the equivalent of the MHS's mechanism. It creates an instance of  
 * DefaultFileSaveDistributionEnvelopeHandler when instantiated itself, and uses this to
 * handle received ITK messages unless a specific handler is registered against the received
 * ITK service.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class ITKTrunkHandler 
    implements SpineEbXmlHandler
{
    
    private static final int ITKATTACHMENT = 0;
    private static final String LOGSOURCE = "ITKTrunkHandler";

    private HashMap<String, DistributionEnvelopeHandler> handlers = null;
    private DefaultFileSaveDistributionEnvelopeHandler defaultHandler = null;
    private Exception bootException = null;
    
    public ITKTrunkHandler()
    {
        handlers = new HashMap<>();
        try {
            defaultHandler = new DefaultFileSaveDistributionEnvelopeHandler();
        }
        catch (java.io.FileNotFoundException e) {
            bootException = e;
        }
    }
    
    /**
     * Register an instance of a handler, against the given ITK service.
     * 
     * @param s ITK service name
     * @param h Instance of a DistributionEnvelopeHandler implementation.
     */
    public void addHandler(String s, DistributionEnvelopeHandler h)
    {
        handlers.put(s, h);
    }
    
    @Override
    public void handle(EbXmlMessage m) 
        throws Exception
    {
         if (bootException != null)
             throw new Exception("Boot exception in ITK Trunk handler", bootException);
         
         // Note that we assume that this is a good enough ITK Trunk message at this point
         // because if it wasn't, the attempt to make the ITKDE attachment would have failed
         // when the EbXmlMessage was parsed off the network. If it *does* throw a ClassCastException,
         // let it because something else has gone wrong in the code.
         //
         ITKDistributionEnvelopeAttachment a = (ITKDistributionEnvelopeAttachment)m.getAttachments().get(ITKATTACHMENT);
         DistributionEnvelope d = a.getDistributionEnvelope();
         DistributionEnvelopeHandler h = handlers.get(d.getService());
         if (h == null) {
             defaultHandler.handle(d);
         } else {
             h.handle(d);
         }
    }
            
}
