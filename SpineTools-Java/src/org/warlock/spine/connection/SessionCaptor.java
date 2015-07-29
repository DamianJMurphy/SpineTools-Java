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
import org.warlock.spine.messaging.Sendable;
/**
 * Interface for classes that implement "session capture" for outbound messages.
 * 
 * <i><b>Implementations of this interface are REQUIRED to be thread-safe</b></i>
 * 
 * @author Damian Murphy damian.murphy@hscic.gov.uk
 */
public interface SessionCaptor {
    
    /**
     * Do something to capture the session details. This is called from the Spine Tools
     * transmitter if an implementation is declared using the <code>org.warlock.spine.messaging.sessioncaptureclass</code>
     * property. The Session getOnTheWireRequest() and getOnTheWireResponse() methods can be used to extract
     * session content.
     * 
     * Implementations of this method MUST absorb their own exceptions. The method is called from within the 
     * message transmission but is not related to the transmission itself.
     * 
     * @param s The Sendable to capture. 
     */
    public void capture(Sendable s);
}
