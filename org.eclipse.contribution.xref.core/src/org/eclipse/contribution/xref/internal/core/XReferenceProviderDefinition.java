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

import org.eclipse.contribution.xref.core.IXReferenceProvider;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;

/**
 * A wrapper for all eclipse.org.contributions.xref.core.providers
 */
public class XReferenceProviderDefinition {

	private IXReferenceProvider provider;
	private String label;
	private String id;
	private boolean enabled;
	private boolean defaultEnablement;

	public XReferenceProviderDefinition(IConfigurationElement config)
		throws CoreException {
		Object obj = config.createExecutableExtension("class");
		if (obj instanceof IXReferenceProvider) {
			provider = (IXReferenceProvider) obj;
		}
		label = config.getAttribute("label");
		id = config.getAttribute("id");
		enabled =
			Boolean.valueOf(config.getAttribute("enabled")).booleanValue();
		defaultEnablement = enabled;
	}

	/**
	 * @return the label of the IXReferenceProvider
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * @return the ID of the IXReferenceProvider
	 */
	public String getID() {
		return id;
	}

	/**
	 * @return whether or not the IXReferenceProvider is enabled
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * sets the IXReferenceProvider enabled status
	 */
	public void setEnabled(boolean newEnabledStatus) {
		enabled = newEnabledStatus;
	}

	/**
	 * @return the description of the IXReferenceProvider
	 */
	public String getDescription() {
		return provider.getProviderDescription();
	}

	/**
	 * @return the IXReferenceProvider
	 */
	public IXReferenceProvider getProvider() {
		return provider;
	}
	

	/**
	 * @return Returns the defaultEnablement.
	 */
	public boolean getDefaultEnablementValue() {
		return defaultEnablement;
	}

}
