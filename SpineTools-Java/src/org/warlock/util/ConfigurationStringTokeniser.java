/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.warlock.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
/**
 *
 * @author DAMU2
 */
public class ConfigurationStringTokeniser {

    private ArrayList<String> tokens = new ArrayList<String>();
    private Iterator<String> tokenSet = null;

    public ConfigurationStringTokeniser(String s) {
        char[] characters = s.trim().toCharArray();
        ArrayDeque<Character> encaps = new ArrayDeque<Character>();
        StringBuilder sb = null;
        char in_force_quote = 0;

        for (char c : characters) {
            if (Character.isWhitespace(c)) {
                if ((in_force_quote == 0) && (encaps.isEmpty())) {
                    if (sb != null) {
                        tokens.add(sb.toString());
                        sb = null;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (sb == null) {
                    sb = new StringBuilder();
                }
                sb.append(c);
                // Handle encapsulation
                // If c is a quote, the "turn off" all encapsulation processing
                if ((c == '\'') || (c == '"')) {
                    if (c == in_force_quote) {
                        in_force_quote = 0;
                    } else {
                        in_force_quote = c;
                    }
                } else {
                    // Handle nested quotes
                    // This *will* end up in a horrible mess if the input is badly-formed,
                    // but we don't need to try to analyse why there is a problem, just
                    // detect it.
                    if ((c == '[') || (c == '(')) {
                        encaps.addFirst(Character.valueOf(c));
                    } else {
                        if (!encaps.isEmpty()) {
                            if (c == ']') {
                                if (encaps.getFirst() == Character.valueOf('[')) {
                                    encaps.removeFirst();
                                }
                            } else {
                                if (c == ')') {
                                    if (encaps.getFirst() == Character.valueOf('(')) {
                                        encaps.removeFirst();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (sb != null) {
            tokens.add(sb.toString());
        }
        tokenSet = tokens.iterator();
    }

    public int countTokens() { return tokens.size(); }
    
    public String nextToken()
        throws Exception
    {
        return tokenSet.next();
    }

    public boolean hasMoreTokens() {
        return tokenSet.hasNext();
    }
}
