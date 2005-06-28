/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation 
 * 				 Helen Hawkins   - iniital version
 ******************************************************************************/
package org.eclipse.ajdt.core.tests.testutils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.ajdt.core.IAJLogger;

/**
 * Logger to help with builder tests
 */
public class TestLogger implements IAJLogger {

    List log;
    
    /* (non-Javadoc)
     * @see org.eclipse.ajdt.core.IAJLogger#log(java.lang.String)
     */
    public void log(String msg) {
        if (log == null) {
            log = new ArrayList();
        }
        log.add(msg);
    }

    public boolean containsMessage(String msg) {
        for (Iterator iter = log.iterator(); iter.hasNext();) {
            String logEntry = (String) iter.next();
            StringBuffer sb = new StringBuffer(logEntry);
            if (sb.indexOf(msg) != -1) {
                return true;
            }
        }
        return false;
    }
    
    public int numberOfEntriesForMessage(String msg) {
        int occurances = 0;
        for (Iterator iter = log.iterator(); iter.hasNext();) {
            String logEntry = (String) iter.next();
            StringBuffer sb = new StringBuffer(logEntry);
            if (sb.indexOf(msg) != -1) {
                occurances++;
            }
        }
        return occurances;
    }
    
}
