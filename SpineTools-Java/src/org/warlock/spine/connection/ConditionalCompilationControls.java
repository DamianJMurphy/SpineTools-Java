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

package org.warlock.spine.connection;

/**
 * Conditional compilation controller. This class exists to support static final
 * boolean values that control "conditional compilation".
 * 
 * "TESTHARNESS" that controls whether clear-text sockets are available to the SpineTools
 * packages. Production use of the SpineTools should not need to set the "TESTHARNESS"
 * constant to true - it exists principally to allow use of SpineTools in the ITK
 * Testbench system, without imposing a lot of run-time conditions for other uses.
 * 
 * "ITK_TRUNK" that controls whether an ITK Trunk Hanlder is instantiated. When true,
 * when this is created it introduces a dependency on the "DistributionEnvelopeTools"
 * package which is unnecessary for applications that don't do any ITK messaging. When
 * "false" the ITK Trunk Handler is not instantiated and so the DistributionEnvelopeTools.jar
 * is not required in the distribution.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class ConditionalCompilationControls {
   
    /**
     * Set true to enable clear-text sockets, false otherwise.
     */
    public static final boolean TESTHARNESS = true;
    
    /**
     * Set if clear-text sockets are requested.
     */
    public static boolean cleartext = true;

    /**
     * Set if only using SDS cache
     */
    // public static boolean sdscacheonly = true;
    
    /**
     * Do we dump received messages to stdout for analysis ?
     */
    public static final boolean DUMP_RECEIVED_MESSAGE = true;
    
    /**
     * Set true to enable automatic instantiation of the ITKTrunkHandler.
     */
    public static final boolean ITK_TRUNK = false;
    
    public static final boolean otwMessageLogging = false;
    
    // Counter to determine whether a configurable non-response to sync should happen every 2 interactions
    public static int synccounter = 0;
}
