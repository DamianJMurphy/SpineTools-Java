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
import org.warlock.spine.logging.SpineToolsLogger;

/**
 * Class to provide the Connection Manager's Timer with a means of running the
 * retry process.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class RetryProcessor 
    implements Runnable
{
    @Override
    public void run()
    {
        try {
            ConnectionManager c = ConnectionManager.getInstance();
            c.processRetries();
        }
        catch (Exception e) {
            SpineToolsLogger.getInstance().log("org.warlock.spine.connection.RetryProcessor.run", e);
        }
    }
}
