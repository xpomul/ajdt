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
package org.eclipse.ajdt.ui.tests.buildconfig;

import java.io.ByteArrayInputStream;

import org.eclipse.ajdt.ui.buildconfig.DefaultBuildConfigurator;
import org.eclipse.ajdt.ui.buildconfig.IBuildConfigurator;
import org.eclipse.ajdt.ui.buildconfig.IProjectBuildConfigurator;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

/**
 * @author hawkinsh
 *  
 */
public class BuildConfiguratorTest extends UITestCase {

	IProject ajProject = null;
	IProject javaProject = null;

	IFile fileA;
	IFile fileB;
	IFile fileDef;

	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();

		ajProject = createPredefinedProject("AJ Project For BuildConfigurationTest"); //$NON-NLS-1$
		waitForJobsToComplete();
		javaProject = createPredefinedProject("java.project.Y"); //$NON-NLS-1$
		
		setupSandboxSourceFolder();
		waitForJobsToComplete();

	}

	private void setupSandboxSourceFolder() throws Exception {
		IFolder src = ajProject.getFolder("testSrcPath"); //$NON-NLS-1$
		if (!src.exists()) {
			src.create(true, true, null);
		}
		IJavaProject jp = JavaCore.create(ajProject);
		IClasspathEntry[] cpes = jp.getRawClasspath();
		IClasspathEntry[] newCpes = new IClasspathEntry[cpes.length + 1];

		boolean alreadyThere = false;
		for (int i = 0; i < cpes.length; i++) {
			newCpes[i] = cpes[i];
			if (cpes[i].getPath().equals(src.getFullPath()))
				alreadyThere = true;
		}
		if (!alreadyThere) {
			newCpes[cpes.length] = JavaCore.newSourceEntry(src.getFullPath());
			jp.setRawClasspath(newCpes, null);
		}

		fileDef = src.getFile("InDefaultPack.java"); //$NON-NLS-1$
		if (!fileDef.exists()) {
			//fileDef.create(new StringBufferInputStream("public class
			// InDefaultPack{}"), true, null);
			String content = "public class InDefaultPack{}"; //$NON-NLS-1$
			ByteArrayInputStream source = new ByteArrayInputStream(content
					.getBytes());
			fileDef.create(source, true, null);
		}
		IFolder pack = src.getFolder("package1"); //$NON-NLS-1$
		if (!pack.exists()) {
			pack.create(true, true, null);
		}

		fileA = pack.getFile("A.java"); //$NON-NLS-1$
		if (!fileA.exists()) {
			//fileA.create(new StringBufferInputStream("package
			// package1;\npublic class A{}"), true, null);
			String content = "package package1;\npublic class A{}"; //$NON-NLS-1$
			ByteArrayInputStream source = new ByteArrayInputStream(content
					.getBytes());
			fileA.create(source, true, null);
		}

		fileB = pack.getFile("B.java"); //$NON-NLS-1$
		if (!fileB.exists()) {
			//fileB.create(new StringBufferInputStream("package
			// package1;\npublic class B{}"), true, null);
			String content = "package package1;\npublic class B{}"; //$NON-NLS-1$
			ByteArrayInputStream source = new ByteArrayInputStream(content
					.getBytes());
			fileB.create(source, true, null);
		}
	}

	public void testGetProjectBuildConfigurator() throws CoreException {
		IBuildConfigurator conf = DefaultBuildConfigurator.getBuildConfigurator();
		IProjectBuildConfigurator pbc;

		pbc = conf.getProjectBuildConfigurator(javaProject);
		if (pbc != null)
			fail("Could obtain a ProjectBuildConfigurator for non-aj project. This should not be possible."); //$NON-NLS-1$

		waitForJobsToComplete();

		ajProject.close(null);

		waitForJobsToComplete();

		pbc = conf.getProjectBuildConfigurator(ajProject);
		if (pbc != null)
			fail("Could obtain a ProjectBuildConfigurator for closed project. This should not be possible."); //$NON-NLS-1$

		waitForJobsToComplete();

		ajProject.open(null);
		pbc = conf.getProjectBuildConfigurator(ajProject);
		if (pbc == null)
			fail("Could not get a ProjectBuildConfigurator for an aj project."); //$NON-NLS-1$

		//test does not work. buildConfigurator gets not notified when
		// selection changes...
		//		PackageExplorerPart.getFromActivePerspective().selectAndReveal(ajProject);
		//		ProjectBuildConfigurator pbc2 =
		// conf.getActiveProjectBuildConfigurator();
		//		if (pbc2 != pbc){
		//				fail("getActiveProjectBuildConfigurator did not return the pbc of the
		// selected project.");
		//		}
	}
}

