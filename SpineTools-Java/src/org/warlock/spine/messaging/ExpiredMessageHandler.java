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

/**
 * Interface for handler classes called when reliable ebXml messages expire either
 * their maximum retry count, or persistDuration. The Connection Manager will automatically
 * save a copy of an expired message to a directory configured by system properties. If
 * any specific behaviour is required, implement this interface and register an instance
 * of the implementation against the SOAP action(s) of the message types to be handled. The
 * Connection Manager will call that implementation after saving the expired message.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */

public interface ExpiredMessageHandler {    
    public void handleExpiry(Sendable s) throws Exception;
}
