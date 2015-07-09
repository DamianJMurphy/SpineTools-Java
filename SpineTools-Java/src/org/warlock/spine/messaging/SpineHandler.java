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
 * This is a "dummy" super-interface for the purposes of giving the ConnectionManager
 * something to store handlers against, in case we ever need to do "receiver" for 
 * Spine SOAP. However, since the thing that gets a handler knows whether it is 
 * dealing with ebXml or SOAP, it will always case to the appropriate sub-interface
 * rather than trying to use SpineHandler.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public interface SpineHandler {

}
