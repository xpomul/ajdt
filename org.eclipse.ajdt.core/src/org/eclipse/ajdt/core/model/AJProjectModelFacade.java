/*******************************************************************************
 * Copyright (c) 2008 SpringSource and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *      SpringSource
 *      Andrew Eisenberg (initial implementation)
 *******************************************************************************/
package org.eclipse.ajdt.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aspectj.ajde.core.AjCompiler;
import org.aspectj.ajdt.internal.core.builder.AjState;
import org.aspectj.ajdt.internal.core.builder.IncrementalStateManager;
import org.aspectj.asm.HierarchyWalker;
import org.aspectj.asm.IHierarchy;
import org.aspectj.asm.IProgramElement;
import org.aspectj.asm.IRelationship;
import org.aspectj.asm.IRelationshipMap;
import org.aspectj.asm.internal.ProgramElement;
import org.aspectj.asm.internal.Relationship;
import org.aspectj.asm.internal.RelationshipMap;
import org.aspectj.bridge.ISourceLocation;
import org.eclipse.ajdt.core.AspectJCore;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.CoreUtils;
import org.eclipse.ajdt.core.javaelements.AJCodeElement;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.core.javaelements.AspectJMemberElement;
import org.eclipse.ajdt.core.javaelements.AspectJMemberElementInfo;
import org.eclipse.ajdt.core.javaelements.CompilationUnitTools;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.JavaElement;

/**
 * 
 * @created Sep 8, 2008
 *
 * This class is a facade for the AspectJ compiler structure model and relationship
 * map.
 * <p> 
 * One object of this class exists for each AspectJ project.  
 * It is created during a full build and lasts until a clean build or
 * another full build is performed.  
 * <p>
 * Objects of this class should be instantiated using the 
 * {@link AJProjectModelFactory} class.
 */
public class AJProjectModelFacade {
    
    /**
     * This aspect ensures that the class is initialized before any 
     * public operation is performed
     */
    // Commented out because this aspect is causing debugguing problems

    
    public final static IProgramElement ERROR_PROGRAM_ELEMENT = new ProgramElement();
    public final static IJavaElement ERROR_JAVA_ELEMENT = new CompilationUnit(null, "ERROR_JAVA_ELEMENT", null);
    static {
        ERROR_PROGRAM_ELEMENT.setName("ERROR_PROGRAM_ELEMENT");
        ERROR_PROGRAM_ELEMENT.setKind(IProgramElement.Kind.FILE);
    }

    /**
     * The aspectj program hierarchy
     */
    private IHierarchy structureModel;
    
    /**
     * stores crosscutting relationships between structure elements
     */
    private IRelationshipMap relationshipMap;
    
    /**
     * the java project that this project model is associated with
     */
    private final IProject project;
    

    boolean isInitialized;
    
    /**
     * creates a new project model facade for this project
     */
    AJProjectModelFacade(IProject project) {
        this.project = project;
//        this.init();
    }
    
    /**
     * grabs the structure and relationships for this project
     * <p> 
     * called by the before advice in EnsureInitialized aspect
     */
    void init() {
        try {
            AjCompiler compiler = AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project.getProject());
            String compilerId = compiler.getId();
            AjState existingState = IncrementalStateManager.retrieveStateFor(compilerId);
            if (existingState != null) {
                relationshipMap = existingState.getRelationshipMap();
                structureModel = existingState.getStructureModel();
                if (relationshipMap != null && structureModel != null) {
                    isInitialized = true;
                }
            }
        } catch(Exception e) {
            // catch trying to access the model before it is created
        }
    }
    
    /**
     * @return true if the AspectJ project model has been found.  false otherwise.
     */
    public boolean hasModel() {
        return isInitialized;
    }
    
    /**
     * @param handle an AspectJ program element handle
     * @return a program element for the handle, or an empty element
     * if the program element is not found
     */
    public IProgramElement getProgramElement(String handle) {
        return structureModel.findElementForHandle(handle);
    }
    
    /**
     * @return the 1-based line number for the given java element
     */
    public int getJavaElementLineNumber(IJavaElement je) {
        IProgramElement ipe = javaElementToProgramElement(je);
        return ipe.getSourceLocation().getLine();
    }

    /**
     * @return a human readable name for the given Java element that is
     * meant to be displayed on menus and labels.
     */
    public String getJavaElementLinkName(IJavaElement je) {
        IProgramElement ipe = javaElementToProgramElement(je);
        if (ipe != null) {  // null if model isn't initialized
            String name = ipe.toLinkLabelString(false);
            if ((name != null) && (name.length() > 0)) {
                return name;
            }
        }
        // use element name instead, qualified with parent
        if (je.getParent() != null) {
            return je.getParent().getElementName() + '.' + je.getElementName();
        }
        return je.getElementName();
    }

    /**
     * @return a program element that corresponds to the given java element.
     */
    public IProgramElement javaElementToProgramElement(IJavaElement je) {
        if (!isInitialized) {
            return null;
        }
        String ajHandle = je.getHandleIdentifier();
        
        if (isBinaryHandle(ajHandle)) {
            ajHandle = convertToAspectJBinaryHandle(ajHandle);
        }
        
        
        // check to see if we need to replace { (compilation unit) with * (aj compilation unit)
        // must always have a * if the CU ends in .aj even if there are no Aspect elements
        // in the file
        // this occurs because AJDT does not have always have control over 
        // the creation of ICompilationUnits.  See PackageFragment.getCompilationUnit()
        ICompilationUnit cu =  null;
        if (je instanceof IMember) {
            cu = ((IMember) je).getCompilationUnit();
        } else if (je instanceof AJCodeElement) {
            cu = ((AJCodeElement) je).getCompilationUnit();
            // get the occurence count 
            int count = ((AJCodeElement) je).occurrenceCount;
            
            int firstBang = ajHandle.indexOf(JavaElement.JEM_COUNT);
            ajHandle = ajHandle.substring(0, firstBang);
            if (count > 1) {
                // there is more than one element
                // with this name
                ajHandle += "!" + count;
            }
            
        } else if (je instanceof ICompilationUnit) {
            cu = (ICompilationUnit) je;
        }
        if (cu != null &&
                CoreUtils.ASPECTJ_SOURCE_ONLY_FILTER.accept(cu.getResource().getName())) {
            ajHandle = ajHandle.replace(JavaElement.JEM_COMPILATIONUNIT, 
                    AspectElement.JEM_ASPECT_CU);
        }
        
        IProgramElement ipe = structureModel.findElementForHandle(ajHandle);
        if (ipe == null) {
            // occurs when the handles are not working properly
            AspectJPlugin.getDefault().getLog().log(new Status(IStatus.WARNING, AspectJPlugin.PLUGIN_ID, 
                    "Could not find the AspectJ program element for handle: " + ajHandle));
            return ERROR_PROGRAM_ELEMENT;
        }
        return ipe;
    }
    
    
    private String convertToAspectJBinaryHandle(String ajHandle) {
        int packageRootIndex = ajHandle.indexOf(JavaElement.JEM_PACKAGEFRAGMENTROOT);
        int packageIndex = ajHandle.indexOf(JavaElement.JEM_PACKAGEFRAGMENT, packageRootIndex);
        String newHandle = ajHandle.substring(0, packageRootIndex+1) + "binaries" + ajHandle.substring(packageIndex);
        return newHandle;
    }

    private boolean isBinaryHandle(String ajHandle) {
        int jemClassIndex = ajHandle.indexOf(JavaElement.JEM_CLASSFILE);
        if (jemClassIndex != -1) {
            int classFileIndex = ajHandle.indexOf(".class", jemClassIndex);
            if (classFileIndex != -1) {
                return true;
            }
        }
        return false;
    }

    public IJavaElement programElementToJavaElement(IProgramElement ipe) {
        return programElementToJavaElement(ipe.getHandleIdentifier());
    }
    
    public IJavaElement programElementToJavaElement(String ajHandle) {
        // check to see if this is a spurious handle. 
        // For ITDs, the aspectj compiler generates program elements before the
        // rest of the program is in place, and they therfore have no parent.
        // They should not exist and we can ignore them.
        if (ajHandle.charAt(0) != '=') {
            return ERROR_JAVA_ELEMENT;
        }
        
        
        String jHandle = ajHandle;
        // are we dealing with something inside of a classfile?
        // if so, then we have to handle it specially
        // because we want to convert this into a source reference if possible
        int classFileIndex = jHandle.indexOf(JavaElement.JEM_CLASSFILE);
        if (classFileIndex != -1) {
            // now make sure this isn't a code element
            int dotClassIndex = ajHandle.indexOf(".class");
            if (dotClassIndex != -1) {
                char typeChar = ajHandle.charAt(dotClassIndex + ".class".length());
                if (typeChar == AspectElement.JEM_ASPECT_TYPE ||
                        typeChar == JavaElement.JEM_TYPE) {
                    return getElementFromClassFile(jHandle);
                }
            }
        }

        
        if (jHandle.indexOf(AspectElement.JEM_CODEELEMENT) != -1) {
            // because code elements are sub classes of local variables
            // must make the code element's handle look like a local
            // variable's handle
            int countIndex = jHandle.lastIndexOf('!');
            int count = 0;
            if (countIndex != -1) {
                try {
                    count = Integer.parseInt(jHandle.substring(countIndex+1));
                } catch (NumberFormatException e) {
                    count = 1;
                }
                jHandle = jHandle.substring(0, countIndex);
            }
            jHandle += "!0!0!0!0!I";
            if (count > 1) {
                jHandle += "!" + count;
            }
        }
        
        IJavaElement je = AspectJCore.create(jHandle);
        if (je == null) {
            // occurs when the handles are not working properly
            AspectJPlugin.getDefault().getLog().log(new Status(IStatus.WARNING, AspectJPlugin.PLUGIN_ID, 
                    "Could not find the Java program element for handle: " + jHandle));
            return ERROR_JAVA_ELEMENT;
        }
        return je;
    }

    
    private IJavaElement getElementFromClassFile(String jHandle) {
        IProgramElement ipe = structureModel.findElementForHandle(jHandle);
        String packageName = ipe.getPackageName();
        // need to find the top level type
        IProgramElement candidate = ipe;
        while (candidate != null && candidate.getKind() != IProgramElement.Kind.FILE) {
            candidate = candidate.getParent();
        }
        String typeName;
        if (candidate != null) {
            int typeIndex = 0;
            while (typeIndex < candidate.getChildren().size() &&
                    ((IProgramElement) candidate.getChildren().get(typeIndex)).getKind() 
                    == IProgramElement.Kind.IMPORT_REFERENCE) {
                typeIndex++;
            }
            if (typeIndex < candidate.getChildren().size()) {
                typeName = ((IProgramElement) candidate.getChildren().get(typeIndex)).getName();
            } else {
                typeName = "";
            }
        } else {
            typeName = "";
        }        
        String qualifiedName = (packageName.length() > 0 ? packageName + "." : "")
            + typeName;
        try {
            // this gives us the type in the current project,
            // but we don't want this if the type exists as source in 
            // some other project in the workspace.
            IType type = JavaCore.create(project).findType(qualifiedName);
            
            // search the rest of the workspace for this type
            ITypeRoot unit = type.getTypeRoot();
            IResource file = unit.getResource();
            
            // try to find the source
            if (file != null && !file.getFileExtension().equals("jar")) {
                // can we find this as a source file in some project?
                IPath path = unit.getPath();
                IJavaProject otherProject = JavaCore.create(project).getJavaModel().getJavaProject(path.segment(0));
                if (otherProject.exists()) {
                    type = otherProject.findType(qualifiedName);
                    unit = type.getTypeRoot();
                    if (unit instanceof ICompilationUnit) {
                        AJCompilationUnit newUnit = CompilationUnitTools.convertToAJCompilationUnit((ICompilationUnit) unit);
                        unit = newUnit != null ? newUnit : unit;
                    }
                }
                return unit.getElementAt(offsetFromLine(unit, ipe.getSourceLocation()));

            } else {
                // try finding the source by creating a handle identiier
                int classIndex = jHandle.indexOf(".class");
                String newHandle = unit.getHandleIdentifier() + 
                        jHandle.substring(classIndex+".class".length());
                
                IJavaElement newElt = (IJavaElement) AspectJCore.create(newHandle);
                if (newElt instanceof AspectJMemberElement) {
                    AspectJMemberElement ajElt = (AspectJMemberElement) newElt;
                    Object info = ajElt.getElementInfo();
                    if (info instanceof AspectJMemberElementInfo) {
                        AspectJMemberElementInfo ajInfo = (AspectJMemberElementInfo) info;
                        ajInfo.setSourceRangeStart(offsetFromLine(unit, ipe.getSourceLocation()));
                    }
                }
                return newElt;
            }
        } catch (JavaModelException e) {
            return null;
        } catch (NullPointerException e) {
            return null;
        }
    }
    
    private int offsetFromLine(ITypeRoot unit, ISourceLocation sloc) throws JavaModelException {
        if (sloc.getOffset() > 0) {
            return sloc.getOffset();
        }
        
        if (unit instanceof AJCompilationUnit) {
            AJCompilationUnit ajUnit = (AJCompilationUnit) unit;
            ajUnit.requestOriginalContentMode();
        }
        IBuffer buf = unit.getBuffer();
        if (unit instanceof AJCompilationUnit) {
            AJCompilationUnit ajUnit = (AJCompilationUnit) unit;
            ajUnit.discardOriginalContentMode();
        }
        if (buf != null) {
            int requestedLine = sloc.getLine();
            int currentLine = 1;
            int offset = 0;
            while (offset < buf.getLength() && currentLine < requestedLine) {
                if (buf.getChar(offset++) == '\n') {
                    currentLine++;
                }
            }
            while (offset < buf.getLength() && Character.isWhitespace(buf.getChar(offset))) {
                offset++;
            }
            return offset;
        } 
        // no source code
        return 0;
    }


    public boolean hasRuntimeTest(IJavaElement je) {
        if (!isInitialized) {
            return false;
        }
        IProgramElement ipe = javaElementToProgramElement(je);
        List relationships = relationshipMap.get(ipe);
        if (relationships != null) {
            for (Iterator relIter = relationships.iterator(); relIter
                    .hasNext();) {
                IRelationship rel = (IRelationship) relIter.next();
                if (rel.hasRuntimeTest()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A hierarchy walker that trim off branches and cut its walk
     * short
     *
     */
    class CancellableHierarchyWalker extends HierarchyWalker {
        private boolean cancelled = false;
        public IProgramElement process(IProgramElement node) {
            preProcess(node);
            if (!cancelled) {
                node.walk(this);
            } else {
                cancelled = false;
            }
            postProcess(node);
            return node;
        }

        protected void cancel() {
            cancelled = true;
        }
    }

    
    /**
     * find out what java elements are on a particular line
     */
    public List/*IJavaElement*/ getJavaElementsForLine(ICompilationUnit icu, final int line) {
        IProgramElement ipe = javaElementToProgramElement(icu);
        final List/*IProgramElement*/ elementsOnLine = new LinkedList();
        
        // walk the program element to get all ipes on the source line
        // XXX may have an off by 1 error
        ipe.walk(new CancellableHierarchyWalker() {
            protected void preProcess(IProgramElement node) {
                ISourceLocation sourceLocation = node.getSourceLocation();
                if (sourceLocation != null) {
                    if (sourceLocation.getEndLine() < line) {
                        // we don't need to explore the rest of this branch
                        cancel();
                    } else if (sourceLocation.getLine() == line) {
                        elementsOnLine.add(node);
                    }
                }
            }
        });
        // now convert to IJavaElements
        List /*IJavaElement*/ javaElements = new ArrayList(elementsOnLine.size());
        for (Iterator ipeIter = elementsOnLine.iterator(); ipeIter.hasNext();) {
            IProgramElement ipeOnLine = (IProgramElement) ipeIter.next();
            javaElements.add(programElementToJavaElement(ipeOnLine));
        }
        return javaElements;
    }
    
    /**
     * find the relationships of a particular kind for a java element
     */
    public List/*IJavaElement*/ getRelationshipsForElement(IJavaElement je, AJRelationshipType relType) {
        if (!isInitialized) {
            return null;
        }
        IProgramElement ipe = javaElementToProgramElement(je);
        List/*Relationship*/ relationships = relationshipMap.get(ipe);
        if (relationships != null) {
            List/*IJavaElement*/ relatedJavaElements = new ArrayList(relationships.size());
            if (relationships != null) {
                for (Iterator iterator = relationships.iterator(); iterator.hasNext();) {
                    Relationship rel = (Relationship) iterator.next();
                    if (relType.getDisplayName().equals(rel.getName())) {
                        for (Iterator targetIter = rel.getTargets().iterator(); targetIter
                                .hasNext();) {
                            String handle = (String) targetIter.next();
                            IJavaElement targetJe = programElementToJavaElement(handle);
                            if (targetJe != null) {
                                relatedJavaElements.add(targetJe);
                            } else {
                                AspectJPlugin.getDefault().getLog().log(new Status(IStatus.WARNING, AspectJPlugin.PLUGIN_ID, "Could not create a Java element with handle:\n" + handle 
                                        + "\nthis probably means that something is wrong with AspectJ's handle creation mechansim.\n" +
                                        "Post this to the AJDT mailing list and an AJDT developer can provide some feedback on this."));
                            }
                        }
                    }
                }
            }
            return relatedJavaElements;
        } else {
            // means something was wrong.
            // something not initialized or the
            // java handles don't mesh with the aspectj handles
            return Collections.EMPTY_LIST;
        }
    }
    
    /**
     * walks the file and grabs all relationships for it
     * could cache this to go faster
     */
    public Map/*Integer,List<IRelationship>*/ getRelationshipsForFile(ICompilationUnit icu) {
        // walk the hierarchy and get relationships for each node
        final Map/*Integer, List<IRelationship>*/ allRelationshipsMap = new HashMap();
        IProgramElement ipe = javaElementToProgramElement(icu);
        ipe.walk(new HierarchyWalker() {
            protected void preProcess(IProgramElement node) {
                List/*IRelationship*/ nodeRels = relationshipMap.get(node);
                if (nodeRels != null && nodeRels.size() > 0) {
                    List/*IRelationship*/ allRelsForLine;
                    Integer line = new Integer(node.getSourceLocation().getLine());
                    if (allRelationshipsMap.containsKey(line)) {
                        allRelsForLine = (List) allRelationshipsMap.get(line);
                    } else {
                        allRelsForLine = new LinkedList();
                        allRelationshipsMap.put(line, allRelsForLine);
                    }
                    allRelsForLine.addAll(nodeRels);
                }                
            }
        });
        return allRelationshipsMap;
    }
    
    /**
     * I don't like how the 3 methods getRelationshipsForXXX return very different things.
     * I am trying to be efficient and not do too much processing on my end, but this leads
     * to having different return types.  Maybe return each as an iterator.  That would be nice.
     */
    public List/*IRelationship*/ getRelationshipsForProject(AJRelationshipType[] relType) {
        Set interesting = new HashSet();
        for (int i = 0; i < relType.length; i++) {
            interesting.add(relType[i].getDisplayName());
        }
        if (relationshipMap instanceof RelationshipMap) {
            RelationshipMap map = (RelationshipMap) relationshipMap;
            // flatten and filter the map
            List allRels = new LinkedList();
            for (Iterator relListIter = map.values().iterator(); relListIter.hasNext();) {
                List/*IRelationship*/ relList = (List) relListIter.next();
                for (Iterator relIter = relList.iterator(); relIter.hasNext();) {
                    IRelationship rel = (IRelationship) relIter.next();
                    if (interesting.contains(rel.getName())) {
                        allRels.add(rel);
                    }
                }
            }
            return allRels;
        } else {
            // shouldn't happen
            return Collections.EMPTY_LIST;
        }
    }
    
    public boolean isAdvised(IJavaElement elt) {
        if (!isInitialized) {
            return false;
        }
        
        IProgramElement ipe = javaElementToProgramElement(elt);
        List rels = relationshipMap.get(ipe);
        if (rels != null && rels.size() > 0) {
            return true;
        } else {
            // check children
            List /*IProgramElement*/ ipeChildren = ipe.getChildren();
            if (ipeChildren != null) {
                for (Iterator childIter = ipeChildren.iterator(); childIter
                        .hasNext();) {
                    IProgramElement child = (IProgramElement) childIter.next();
                    rels = relationshipMap.get(child);
                    if (rels != null && rels.size() > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    public Set /* IJavaElement */ aspectsForFile(ICompilationUnit cu) {
        IProgramElement ipe = javaElementToProgramElement(cu);
        // compiler should be able to do this for us, but functionality is
        // not exposed. so let's do it ourselves
        final Set /* IJavaElement */ aspects = new HashSet();
        ipe.walk(new HierarchyWalker() {
            protected void preProcess(IProgramElement node) {
                if (node.getKind() == IProgramElement.Kind.ASPECT) {
                    aspects.add(programElementToJavaElement(node));
                }
            }
        });
        return aspects;
    }
    
    void dispose() {
        structureModel = null;
        relationshipMap = null;
        isInitialized = false;
    }

    public IProject getProject() {
        return project;
    }
}