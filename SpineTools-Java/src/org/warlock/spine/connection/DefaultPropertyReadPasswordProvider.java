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
 * Default implementation of the PasswordProvider.
 * 
 * @deprecated INSECURE
 * 
 * This class is provided as an example, and for test purposes only. <b>It is not 
 * intended to be suitable for production use.</b>
 * 
 * Passwords for this class are stored insecurely, in clear, in any properties file
 * that feeds System.properties, against "org.warlock.spine.connection.password", and
 * will support retrieval of other passwords by type, where the password type is used
 * as the property name.
 * 
 * If you know that your server is secure, or you don't care, then this class will work
 * as a password provider. You can even remove the deprecation in that case. But you 
 * have been warned.
 * 
 * @author Damian Murphy <damian.murphy@hscic.gov.uk>
 */
public class DefaultPropertyReadPasswordProvider 
    implements PasswordProvider
{
   private static final String PASSWORD_PROPERTY = "org.warlock.spine.connection.password"; 
   
   @Override
   public String getPassword()
   {
       String p = System.getProperty(PASSWORD_PROPERTY);
       if (p == null)
           return "";
       return p;
   }
   
   @Override
   public String getPassword(String passwordType)
           throws Exception
   {
       return System.getProperty(passwordType);
   }
}
