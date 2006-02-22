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
package org.eclipse.ajdt.ui.tests.utils;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.internal.utils.AJDTUtils;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.pde.core.plugin.IPluginImport;
import org.eclipse.pde.core.plugin.IPluginModel;
import org.eclipse.pde.internal.ui.editor.plugin.ManifestEditor;

/**
 * @author hawkinsh
 * 
 */
public class AJDTUtilsTest extends UITestCase {

	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testAddAndRemoveAspectJNatureWithPluginProject()
			throws Exception {
		setUpPluginEnvironment();
		IProject testPluginProject = createPredefinedProject("Hello World Java Plugin"); //$NON-NLS-1$
		waitForJobsToComplete();
		assertFalse("Plugin project shouldn't have AspectJ nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testPluginProject.getProject()));
		assertFalse("Plugin should not import AJDE plugin", //$NON-NLS-1$
				hasDependencyOnAJDE(testPluginProject));
		AspectJUIPlugin.convertToAspectJProject(testPluginProject.getProject());
		waitForJobsToComplete();
		assertTrue("Plugin project should now have AspectJ nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testPluginProject.getProject()));
		assertTrue("Plugin should now import AJDE plugin", //$NON-NLS-1$
				hasDependencyOnAJDE(testPluginProject));
		AJDTUtils.removeAspectJNature(testPluginProject.getProject());
		waitForJobsToComplete();
		assertFalse("Plugin should not import AJDE plugin", //$NON-NLS-1$
				hasDependencyOnAJDE(testPluginProject));
		assertFalse("Plugin project shouldn't have AspectJ nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testPluginProject.getProject()));
		resetPluginEnvironment();
	}

	public void testAddAndRemoveAspectJNature() throws CoreException {
		IProject testProject = createPredefinedProject("project.java.Y"); //$NON-NLS-1$
		IJavaProject jY = JavaCore.create(testProject);
		waitForJobsToComplete();
		
		assertFalse("Java project should not have AspectJ Nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testProject.getProject()));
		assertFalse("Build path shouldn't contain aspectjrt.jar", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));
		AspectJUIPlugin.convertToAspectJProject(testProject.getProject());
		assertTrue("Java project should now have AspectJ Nature", AspectJPlugin //$NON-NLS-1$
				.isAJProject(testProject.getProject()));
		assertTrue("Build path should now contain aspectjrt.jar", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));
		AJDTUtils.removeAspectJNature(testProject.getProject());
		assertFalse("Java project should not have AspectJ Nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testProject.getProject()));
		assertFalse("Build path shouldn't contain aspectjrt.jar",hasAjrtOnBuildPath(jY)); //$NON-NLS-1$
	}
	
	/**
	 * Test for bug 93532 - NPE when add aspectj nature to a plugin project
	 * which doesn't have a plugin.xml file.
	 * 
	 * @throws Exception
	 */
	public void testBug93532() throws Exception {
		IProject testProject = createPredefinedProject("bug93532"); //$NON-NLS-1$
		IJavaProject jY = JavaCore.create(testProject);
		waitForJobsToComplete();
		
		assertFalse("Java project should not have AspectJ Nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testProject.getProject()));
		assertFalse("Build path shouldn't contain aspectjrt.jar", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));
		AspectJUIPlugin.convertToAspectJProject(testProject.getProject());
		assertTrue("Java project should now have AspectJ Nature", AspectJPlugin //$NON-NLS-1$
				.isAJProject(testProject.getProject()));
		assertTrue("Build path should now contain aspectjrt.jar", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));
		AJDTUtils.removeAspectJNature(testProject.getProject());
		assertFalse("Java project should not have AspectJ Nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testProject.getProject()));
		assertFalse("Build path shouldn't contain aspectjrt.jar",hasAjrtOnBuildPath(jY)); //$NON-NLS-1$
	}

	/**
	 * This tests whether you get back the manifest editor for the project you
	 * require.
	 * 
	 */
	public void testGetPDEManifestEditor() throws Exception {
		setUpPluginEnvironment();
		// know that the plugin id of this is HelloWorld
		IProject projectA1 = createPredefinedProject("Hello World Java Plugin"); //$NON-NLS-1$
		waitForJobsToComplete();
		
		// know that the plugin id for this is PluginWithView
		IProject projectA2 = createPredefinedProject("PluginWithView"); //$NON-NLS-1$
		waitForJobsToComplete();

		assertTrue("projectA1 should have manifest editor for project A1", //$NON-NLS-1$
				AJDTUtils.getAndPrepareToChangePDEModel(projectA1.getProject())
						.getPartName().equals("HelloWorld")); //$NON-NLS-1$
		assertTrue("projectA2 should have manifest editor for project A2", //$NON-NLS-1$
				AJDTUtils.getAndPrepareToChangePDEModel(projectA2.getProject())
						.getPartName().equals("PluginWithView")); //$NON-NLS-1$
		resetPluginEnvironment();
	}

	// Do not delete this test - if we ever change the way we deal with 
	// project dependencies, then need this test
	// We now longer change project dependencies in this way, so test removed
//	public void testChangeProjectToClassDependencies() throws Exception {
//		JavaTestProject jtp1 = new JavaTestProject("JavaTestProject1");
//		Utils.waitForJobsToComplete();
//		JavaTestProject jtp2 = new JavaTestProject("JavaTestProject2");
//		Utils.waitForJobsToComplete();
//		// this ensures a src folder is created.
//		jtp2.getSourceFolder();
//		Utils.waitForJobsToComplete();
//		ProjectDependenciesUtils.addProjectDependency(jtp1.getJavaProject(),
//				jtp2.getProject());
//		Utils.waitForJobsToComplete();
//		assertTrue("test project 1 has a project dependency on test project 2",
//				checkDependencyType(jtp1.getJavaProject(), jtp2.getProject())
//						.equals("project"));
//		AJDTUtils.changeProjectDependencies(jtp2.getProject());
//		Utils.waitForJobsToComplete();
//		assertTrue(
//				"test project 1 has a class folder dependency on test project 2",
//				checkDependencyType(jtp1.getJavaProject(), jtp2.getProject())
//						.equals("classfolder"));
//		jtp1.dispose();
//		jtp2.dispose();
//	}

	public void testAddAndRemoveAjrtToBuildPath() throws Exception {
		IProject projectY = createPredefinedProject("project.java.Y"); //$NON-NLS-1$
		IJavaProject jY = JavaCore.create(projectY);
		waitForJobsToComplete();

		assertFalse("project.java.Y should not have ajrt on build path", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));
		AspectJUIPlugin.addAjrtToBuildPath(projectY);
		waitForJobsToComplete();

		assertTrue("project.java.Y should have ajrt on build path", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));

		AspectJUIPlugin.removeAjrtFromBuildPath(projectY);
		waitForJobsToComplete();
		assertFalse("project.java.Y should not have ajrt on build path", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));
	}

	private boolean hasAjrtOnBuildPath(IJavaProject javaProject) {
		try {
			IClasspathEntry[] originalCP = javaProject.getRawClasspath();
			for (int i = 0; i < originalCP.length; i++) {
				IPath path = originalCP[i].getPath();
				if (path.toOSString().endsWith("ASPECTJRT_LIB") //$NON-NLS-1$
						|| path.toOSString().endsWith("aspectjrt.jar")) { //$NON-NLS-1$
					return true;
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return false;
	}

	// private void addImportToPDEModel(IPluginModel model, String importId)
	// throws CoreException {
	//
	// IPluginImport importNode = model.getPluginFactory().createImport();
	// importNode.setId(importId);
	// model.getPluginBase().getImports();
	// model.getPluginBase().add(importNode);
	//
	// IFile manifestFile = (IFile) model.getUnderlyingResource();
	// manifestFile.refreshLocal(IResource.DEPTH_INFINITE, null);
	// Utils.waitForJobsToComplete();
	// }

	private boolean hasDependencyOnAJDE(IProject project) {
		ManifestEditor manEd = AJDTUtils
				.getAndPrepareToChangePDEModel(project);
		if (manEd == null) {
			return false;
		}
		IPluginModel model = (IPluginModel) manEd.getAggregateModel();
		IPluginImport[] imports = model.getPluginBase().getImports();

		for (int i = 0; i < imports.length; i++) {
			IPluginImport importObj = imports[i];
			if (importObj.getId().equals(AspectJPlugin.RUNTIME_PLUGIN_ID)) {
				return true;
			}
		}
		return false;
	}

	public static class MyJobChangeListener implements IJobChangeListener {

		private List scheduledBuilds = new ArrayList();

		public void aboutToRun(IJobChangeEvent event) {
		}

		public void awake(IJobChangeEvent event) {
		}

		public void done(IJobChangeEvent event) {
			if (event.getJob().getPriority() == Job.BUILD) {
				System.out.println(">> finished a build"); //$NON-NLS-1$
				scheduledBuilds.remove(event.getJob());
			}

		}

		public void running(IJobChangeEvent event) {
		}

		public void scheduled(IJobChangeEvent event) {
			if (event.getJob().getPriority() == Job.BUILD) {
				System.out.println(">> scheduled a build"); //$NON-NLS-1$
				scheduledBuilds.add(event.getJob());
			}
		}

		public void sleeping(IJobChangeEvent event) {
		}

		public boolean buildsAreScheduled() {
			return !(scheduledBuilds.isEmpty());
		}

	}

}
