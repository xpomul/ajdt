/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     Matt Chapman - initial version
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests.ajde;

import junit.framework.TestCase;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.internal.utils.AJDTUtils;
import org.eclipse.ajdt.ui.tests.testutils.Utils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * This test checks that the order of entries on the classpath is preserved
 * after conversion from a Java project to an AspectJ project. Rather than
 * testing the classpath directly, it only tests for compilation errors. The
 * test project is set up to have one "real" version of a class, and five "fake"
 * versions, after the real one on the classpath. Therefore there is a small
 * chance that the classpath order could be changed, but in such a way that the
 * test still passes. However this is a more real-world test than just
 * performing string operations on the classpath.
 * 
 * @author mchapman
 */
public class ClasspathOrderTest extends TestCase {

	IProject project;

	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		project = Utils.createPredefinedProject("ClasspathOrdering");
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		Utils.deleteProject(project);
	}
	
	public void testClasspathOrder() throws Exception {
		assertFalse(
				"ClasspathOrdering project shouldn't yet have AspectJ nature",
				AspectJPlugin.isAJProject(project));

		String filename = "src/foo/OrderTest.java";
		IResource res = project.findMember(filename);
		if (res == null) {
			fail("Required file not found: " + filename);
		}
		ITextEditor editorPart = (ITextEditor) Utils.openFileInDefaultEditor(
				(IFile) res, false);
		Utils.waitForJobsToComplete();

		boolean foundError = false;
		IMarker[] markers = getMarkers(res, editorPart);
		for (int i = 0; !foundError && (i < markers.length); i++) {
			IMarker m = markers[i];
			Integer sev = (Integer) m.getAttribute(IMarker.SEVERITY);
			if (sev.intValue() == IMarker.SEVERITY_ERROR) {
				foundError = true;
			}
		}
		assertFalse("Java project has errors", foundError);

		AJDTUtils.addAspectJNature(project);
		Utils.waitForJobsToComplete();
		assertTrue("ClasspathOrdering project should now have AspectJ nature",
				AspectJPlugin.isAJProject(project));
		project.build(IncrementalProjectBuilder.FULL_BUILD, null);
		Utils.waitForJobsToComplete();

		foundError = false;
		markers = getMarkers(res, editorPart);
		for (int i = 0; !foundError && (i < markers.length); i++) {
			IMarker m = markers[i];
			Integer sev = (Integer) m.getAttribute(IMarker.SEVERITY);
			if (sev.intValue() == IMarker.SEVERITY_ERROR) {
				foundError = true;
			}
		}
		assertFalse("AspectJ project has errors", foundError);

	}

	protected IMarker[] getMarkers(IResource resource, ITextEditor editor)
			throws Exception {
		if (resource instanceof IFile)
			return resource.findMarkers(
					IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true,
					IResource.DEPTH_INFINITE);
		else {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			return root.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER,
					true, IResource.DEPTH_INFINITE);
		}
	}

}