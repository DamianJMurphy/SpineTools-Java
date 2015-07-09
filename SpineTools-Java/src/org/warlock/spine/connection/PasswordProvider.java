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
 * Interface definition for the Password Providers used to supply passwords to
 * the Security Context.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public interface PasswordProvider {
    /**
     * Return the default password for the context itself. By default the same is
     * used for both key store and trust store.
     * @return 
     */
    public String getPassword();
    
    /**
     * Return the named type of password.
     * @param passwordType
     * @return
     * @throws Exception 
     */
    public String getPassword(String passwordType) throws Exception;
}
