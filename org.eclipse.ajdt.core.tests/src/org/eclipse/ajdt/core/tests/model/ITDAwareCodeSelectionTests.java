/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

package org.eclipse.ajdt.core.tests.model;

import java.util.HashMap;

import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.contribution.jdt.itdawareness.INameEnvironmentProvider;
import org.eclipse.contribution.jdt.itdawareness.ITDAwarenessAspect;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.internal.compiler.SourceElementParser;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.env.ISourceType;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.SearchableEnvironment;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

/**
 * @author Andrew Eisenberg
 * @created Jun 6, 2009
 * Tests code selection for ITDs
 */
public class ITDAwareCodeSelectionTests extends AJDTCoreTestCase {
    IProject project;
    IFile targetFile;
    IFile otherFile;
    IFile aspectFile;

    ICompilationUnit targetUnit;
    ICompilationUnit otherUnit;
    ICompilationUnit aspectUnit;
    
    // need to set a NameEnviromentProvider, since this is typically
    // set by AJDT.UI
    // BAD!!!
    INameEnvironmentProvider origProvider;
    INameEnvironmentProvider mockProvider = new INameEnvironmentProvider() {
    
        public ISourceType transformSourceTypeInfo(ISourceType info) {
            // don't need
            return null;
        }
    
        public boolean shouldFindProblems(CompilationUnit unitElement) {
            return true;
        }
    
        public CompilationUnitDeclaration problemFind(CompilationUnit unitElement,
                SourceElementParser parer, WorkingCopyOwner workingCopyOwner,
                HashMap problems, boolean creatingAST, int reconcileFlags,
                IProgressMonitor monitor) throws JavaModelException {
            // don't need
            return null;
        }
    
        public SearchableEnvironment getNameEnvironment(JavaProject project,
                ICompilationUnit[] workingCopies) {
            // don't need
            return null;
        }
    
        public SearchableEnvironment getNameEnvironment(JavaProject project,
                WorkingCopyOwner owner) {
            // don't need
            return null;
        }
    };
    
    /*
     * @see TestCase#setUp()
     */
    protected void setUp() throws Exception {
        origProvider = ITDAwarenessAspect.provider;
        ITDAwarenessAspect.provider = mockProvider;
        super.setUp();
        project = createPredefinedProject("Bug273334"); //$NON-NLS-1$
        targetFile = project.getFile("src/a/HasAnITD.java");
        otherFile = project.getFile("src/a/AThird.java");
        aspectFile = project.getFile("src/a/DeclaresITDs.aj");
        
        targetUnit = JavaCore.createCompilationUnitFrom(targetFile);
        otherUnit = JavaCore.createCompilationUnitFrom(otherFile);
        aspectUnit = JavaCore.createCompilationUnitFrom(aspectFile);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        ITDAwarenessAspect.provider = origProvider;
    }
    /**
     * Test that ITD hyperlinks work when inside the CU that
     * is a target of the ITD
     */
    public void testITDTargetFileHyperlink() throws Exception {
        validateCodeSelect(targetUnit, findRegion(targetUnit, "field", 1), "HasAnITD.field");
        validateCodeSelect(targetUnit, findRegion(targetUnit, "field", 2), "HasAnITD.field");
        validateCodeSelect(targetUnit, findRegion(targetUnit, "method", 1), "HasAnITD.method");
        validateCodeSelect(targetUnit, findRegion(targetUnit, "method", 2), "HasAnITD.method");
        
    }
    /**
     * Test that ITD hyperlinks work when in another CU
     */
    public void testOtherFileHyperlink() throws Exception {
        validateCodeSelect(otherUnit, findRegion(otherUnit, "field", 1), "HasAnITD.field");
        validateCodeSelect(otherUnit, findRegion(otherUnit, "method", 1), "HasAnITD.method");
        
    }
    /**
     * Test that ITD hyperlinks work when in an aspect
     */
    public void testAspectFileHyperlink() throws Exception {
        validateCodeSelect(aspectUnit, findRegion(aspectUnit, "field", 1), "HasAnITD.field");
        validateCodeSelect(aspectUnit, findRegion(aspectUnit, "method", 1), "HasAnITD.method");
        
    }
    
    private void validateCodeSelect(ICompilationUnit unit,
            IRegion region, String expected) throws Exception {
        IJavaElement[] result = unit.codeSelect(region.getOffset(), region.getLength());
        assertEquals("Should have found exactly one hyperlink", 1, result.length);
        IJavaElement elt = result[0];
        assertTrue("Java element " + elt.getHandleIdentifier() + " should exist", elt.exists());
        assertEquals(expected, elt.getElementName());
    }

    private IRegion findRegion(ICompilationUnit unit, String string, int occurrence) {
        String contents = new String(((CompilationUnit) unit).getContents());
        int start = 0;
        while (occurrence-- > 0) {
            start = contents.indexOf(string, start);
        }
        return new Region(start, string.length());
    }
}