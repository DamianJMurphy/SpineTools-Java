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
/**
 * Interface for DistributionEnvelopeHandler classes, called by the ITK Trunk handler.
 * Note that at most a single instance of each implementation of this interface, is
 * registered against any given ITK service name. As such, <i>implementations <b>MUST</b>
 * be thread-safe</i>. The handle() method on the same instance will be called to process
 * each of multiple distribution envelopes of the same service, that arrive simultaneously.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public interface DistributionEnvelopeHandler {
    public void handle(DistributionEnvelope d) throws Exception;
}
