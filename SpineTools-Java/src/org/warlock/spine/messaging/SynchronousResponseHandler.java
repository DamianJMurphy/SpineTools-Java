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
 * Interface for synchronoous responses. Note that the ConnectionManager keys these
 * on the <i>request type</i>, not the type of the response, because there can be
 * multiple response types for a given request. A single instance of an implementation
 * will be used for all requests, even simultaneous ones, so implementations <b>MUST</b>
 * be thread-safe.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public interface SynchronousResponseHandler {
 
    public void handle(SpineSOAPRequest r) throws Exception;
}
