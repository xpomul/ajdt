/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Helen Hawkins   - iniital version
 *******************************************************************************/
package org.eclipse.contribution.xref.internal.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.contribution.xref.core.IXReferenceProvider;
import org.eclipse.contribution.xref.core.XReference;

/**
 * @author hawkinsh
 *  
 */
public class TestProvider implements IXReferenceProvider {

	public static boolean beBad = false; // for setting up test conditions

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.contribution.xref.core.IXReferenceProvider#getClasses()
	 */
	public Class[] getClasses() {
		return new Class[] { String.class };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.contribution.xref.core.IXReferenceProvider#getXReferences(java.lang.Object)
	 */
	public Collection getXReferences(Object o) {
		String s = (String) o;
		Set a = new HashSet();
		a.add(s.toUpperCase());
		XReference xr = new XReference("In Upper Case", a);
		List l = new ArrayList();
		l.add(xr);
		return l;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.contribution.xref.core.IXReferenceProvider#getProviderDescription()
	 */
	public String getProviderDescription() {
		// let's be deliberately untrustworthy here, to test the SafeExecution
		// aspect
		if (beBad) {
			throw new RuntimeException("You should never trust a provider you know...");
		}
		return "My Description";
	}

}
