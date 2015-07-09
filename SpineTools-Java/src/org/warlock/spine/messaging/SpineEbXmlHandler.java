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
 * Interface for SpineEbXmlHandlers. A single instance of a handler implementation is
 * held by the ConnectionManager, against each SOAP action. That implementation will
 * be called in parallel when multiple instances of a message of a given type are 
 * received simultaneously. Implementations of this interface <b>MUST</b> therefore
 * be thread-safe.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public interface SpineEbXmlHandler 
    extends SpineHandler
{
    public void handle(EbXmlMessage m) throws Exception;
}
