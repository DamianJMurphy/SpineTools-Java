/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.warlock.util;
import java.util.ArrayList;
/**
 *
 * @author DAMU2
 */
public class ConfigurationTokenSplitter {
    
    private ConfigurationStringTokeniser tokeniser = null;
    
    public ConfigurationTokenSplitter(String line) {
        tokeniser = new ConfigurationStringTokeniser(line);
    }
    
    public String[] split() 
            throws Exception
    {
        ArrayList<String> list = new ArrayList<String>();
        while (tokeniser.hasMoreTokens()) {
            String s = tokeniser.nextToken();
            if (s.endsWith("\"")) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.startsWith("\"")) {
                s = s.substring(1);
            }
            list.add(s);
        }
        String[] a = new String[list.size()];
        a = list.toArray(a);
        return a;
    }
}
