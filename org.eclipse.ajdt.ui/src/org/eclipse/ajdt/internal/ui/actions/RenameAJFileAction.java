/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation 
 * 				 Helen Hawkins   - iniital version
 ******************************************************************************/
package org.eclipse.ajdt.internal.ui.actions;

import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameResourceProcessor;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.UserInterfaceStarter;
import org.eclipse.jdt.internal.ui.refactoring.reorg.RenameUserInterfaceManager;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IWorkbenchWindow;

/**
 * Action to rename .aj files
 */
public class RenameAJFileAction implements IActionDelegate {

    /* (non-Javadoc)
     * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
     */
    public void run(IAction action) {
        // the following implementation has been adapted from 
        // org.eclipse.jdt.internal.ui.refactoring.actions.RenameResourceAction.run(..)
		IWorkbenchWindow window= JavaPlugin.getActiveWorkbenchWindow();
		if (window != null) {
		    ISelection sel = window.getSelectionService().getSelection();
		    if (sel instanceof IStructuredSelection) {
				IResource resource = getResource((IStructuredSelection)sel);
				// Work around for http://dev.eclipse.org/bugs/show_bug.cgi?id=19104		
				if (!ActionUtil.isProcessable(window.getShell(), resource))
					return;
				RenameResourceProcessor processor= new RenameResourceProcessor(resource);
				try {
					if(!processor.isApplicable())
						return;
					RenameRefactoring refactoring= new RenameRefactoring(processor);
					UserInterfaceStarter starter= RenameUserInterfaceManager.getDefault().getStarter(refactoring);
					starter.activate(refactoring, window.getShell(), true);
				} catch (CoreException e) {
					ExceptionHandler.handle(e, RefactoringMessages.getString("RenameJavaElementAction.name"), RefactoringMessages.getString("RenameJavaElementAction.exception"));  //$NON-NLS-1$ //$NON-NLS-2$
				}                       
            }

		}
		
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action.IAction, org.eclipse.jface.viewers.ISelection)
     */
    public void selectionChanged(IAction action, ISelection selection) {
    }

	private IResource getResource(IStructuredSelection selection) {
		if (selection.size() != 1)
			return null;
		Object first= selection.getFirstElement();
		if (first instanceof AJCompilationUnit) {
            return ((AJCompilationUnit)first).getResource();
        } 
		return null;
	}
}
