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
import org.warlock.itk.distributionenvelope.DistributionEnvelopeHelper;

/**
 *
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class ITKDistributionEnvelopeAttachment
        extends Attachment {

    private static final String DEFAULT_DESCRIPTION = "ITK Trunk Message";
    private static final String MIME_TYPE = "text/xml";

    private DistributionEnvelope distributionEnvelope = null;

    public ITKDistributionEnvelopeAttachment(String s)
            throws Exception {
        String d = stripMimeHeaders(s);
        DistributionEnvelopeHelper deh = DistributionEnvelopeHelper.getInstance();
        distributionEnvelope = deh.getDistributionEnvelope(d);
        description = DEFAULT_DESCRIPTION;
        mimetype = MIME_TYPE;
    }

    public ITKDistributionEnvelopeAttachment(DistributionEnvelope d)
            throws Exception {
        distributionEnvelope = d;
        description = DEFAULT_DESCRIPTION;
        mimetype = MIME_TYPE;
    }

    /**
     *
     * @return The EBXML Manifest
     */
    @Override
    public String getEbxmlReference() {
        StringBuilder sb = new StringBuilder(REFERENCE);
        substitute(sb, "__CONTENT_SCHEME__", "cid:");
        substitute(sb, "__CONTENT_ID__", contentid);
        StringBuilder rc = new StringBuilder(DESCRIPTIONELEMENT);
        substitute(rc, "__DESCRIPTION__", description);
        substitute(sb, "__REFERENCE_BODY__", rc.toString());
        return sb.toString();
    }

    public DistributionEnvelope getDistributionEnvelope() {
        return distributionEnvelope;
    }

    /**
     *
     * @return String representation of the message with substitutions made
     */
    @Override
    public String serialise() 
    {   
        if (distributionEnvelope.getEnvelope() == null || distributionEnvelope.getEnvelope().trim().length() == 0) {
            return distributionEnvelope.toString();
        }
        return distributionEnvelope.getEnvelope();
    }

}
