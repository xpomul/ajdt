/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     	IBM Corporation - initial API and implementation
 * 		Luzius Meisser - Adapted for use with AspectJ
 *      Andy Clement - updated for AspectJ 5
 *      Sian January - updated for eager parsing
 *******************************************************************************/
package org.eclipse.ajdt.parserbridge;

import java.util.ArrayList;

import org.aspectj.ajdt.internal.compiler.ast.AdviceDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.AspectDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.PointcutDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.CompilerModifiers;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.ISourceElementRequestor;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.IGenericType;
import org.eclipse.jdt.internal.compiler.env.ISourceType;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.impl.ReferenceContext;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.MethodScope;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;
import org.eclipse.jdt.internal.compiler.parser.SourceTypeConverter;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.jdt.internal.compiler.util.HashtableOfObjectToInt;
import org.eclipse.jdt.internal.core.util.CommentRecorderParser;

/*
 * Luzius: 
 * Most content of this has been copied from
 * org.eclipse.jdt.internal.compiler.SourceElementParser
 * 
 * Sian: 
 * Copied and edited AJSourceElementParser to use org.eclipse... JDT types
 * instead of org.aspectj.org.eclipse... JDT types
 */

/**
 * A source element parser extracts structural and reference information
 * from a piece of source.
 *
 * also see @ISourceElementRequestor
 *
 * The structural investigation includes:
 * - the package statement
 * - import statements
 * - top-level types: package member, member types (member types of member types...), aspects
 * - fields
 * - methods
 * - pointcuts
 * - declares
 * - intertype declarations
 *
 * If reference information is requested, then all source constructs are
 * investigated and type, field & method references are provided as well.
 *
 * Any (parsing) problem encountered is also provided.
 */
public class AJSourceElementParser2 extends CommentRecorderParser {
	
	int fieldCount;
	ISourceType sourceType;
	boolean reportReferenceInfo;
	char[][] typeNames;
	char[][] superTypeNames;
	int nestedTypeIndex;
	NameReference[] unknownRefs;
	int unknownRefsCounter;
	LocalDeclarationVisitor localDeclarationVisitor = null;
	CompilerOptions options;
	HashtableOfObjectToInt sourceEnds = new HashtableOfObjectToInt();
	private AJCompilationUnitStructureRequestor requestor;
	
/**
 * An ast visitor that visits local type declarations.
 */
public class LocalDeclarationVisitor extends ASTVisitor {
	ArrayList declaringTypes;
	public void pushDeclaringType(TypeDeclaration declaringType) {
		if (this.declaringTypes == null) {
			this.declaringTypes = new ArrayList();
		}
		this.declaringTypes.add(declaringType);
	}
	public void popDeclaringType() {
		this.declaringTypes.remove(this.declaringTypes.size()-1);
	}
	public TypeDeclaration peekDeclaringType() {
		if (this.declaringTypes == null) return null;
		int size = this.declaringTypes.size();
		if (size == 0) return null;
		return (TypeDeclaration) this.declaringTypes.get(size-1);
	}
	public boolean visit(TypeDeclaration typeDeclaration, BlockScope scope) {
		notifySourceElementRequestor(typeDeclaration, sourceType == null, peekDeclaringType());
		return false; // don't visit members as this was done during notifySourceElementRequestor(...)
	}
	public boolean visit(TypeDeclaration typeDeclaration, ClassScope scope) {
		notifySourceElementRequestor(typeDeclaration, sourceType == null, peekDeclaringType());
		return false; // don't visit members as this was done during notifySourceElementRequestor(...)
	}	
}

public AJSourceElementParser2(
	final AJCompilationUnitStructureRequestor requestor, 
	IProblemFactory problemFactory,
	CompilerOptions options) {
	// we want to notify all syntax error with the acceptProblem API
	// To do so, we define the record method of the ProblemReporter
	super(new ProblemReporter(
		DefaultErrorHandlingPolicies.exitAfterAllProblems(),
		options, 
		problemFactory) {
		public void record(IProblem problem, CompilationResult unitResult, ReferenceContext context) {
			unitResult.record(problem, context); // TODO (jerome) clients are trapping problems either through factory or requestor... is result storing needed?
			requestor.acceptProblem(problem);
		}
	},
	true);
	this.requestor = requestor;
	typeNames = new char[4][];
	superTypeNames = new char[4][];
	nestedTypeIndex = 0;
	this.options = options;
}

public AJSourceElementParser2(
	final AJCompilationUnitStructureRequestor requestor, 
	IProblemFactory problemFactory,
	CompilerOptions options,
	boolean reportLocalDeclarations) {
		this(requestor, problemFactory, options);
		if (reportLocalDeclarations) {
			this.localDeclarationVisitor = new LocalDeclarationVisitor();
		}
}

public void checkComment() {
}

protected void classInstanceCreation(boolean alwaysQualified) {

	boolean previousFlag = reportReferenceInfo;
	reportReferenceInfo = false; // not to see the type reference reported in super call to getTypeReference(...)
	super.classInstanceCreation(alwaysQualified);
	reportReferenceInfo = previousFlag;
	if (reportReferenceInfo){
		AllocationExpression alloc = (AllocationExpression)expressionStack[expressionPtr];
		TypeReference typeRef = alloc.type;
		requestor.acceptConstructorReference(
			typeRef instanceof SingleTypeReference 
				? ((SingleTypeReference) typeRef).token
				: CharOperation.concatWith(alloc.type.getParameterizedTypeName(), '.'),
			alloc.arguments == null ? 0 : alloc.arguments.length, 
			alloc.sourceStart);
	}
}
private long[] collectAnnotationPositions(Annotation[] annotations) {
	if (annotations == null) return null;
	int length = annotations.length;
	long[] result = new long[length];
	for (int i = 0; i < length; i++) {
		Annotation annotation = annotations[i];
		result[i] = (((long) annotation.sourceStart) << 32) + annotation.declarationSourceEnd;
	}
	return result;
}
protected void consumeAnnotationAsModifier() {
	super.consumeAnnotationAsModifier();
	Annotation annotation = (Annotation)expressionStack[expressionPtr];
	if (reportReferenceInfo) { // accept annotation type reference
		this.requestor.acceptTypeReference(annotation.type.getTypeName(), annotation.sourceStart, annotation.sourceEnd);
	}
}
protected void consumeConstructorHeaderName() {
	long selectorSourcePositions = this.identifierPositionStack[this.identifierPtr];
	int selectorSourceEnd = (int) selectorSourcePositions;
	int currentAstPtr = this.astPtr;
	super.consumeConstructorHeaderName();
	if (this.astPtr > currentAstPtr) // if ast node was pushed on the ast stack
		this.sourceEnds.put(this.astStack[this.astPtr], selectorSourceEnd);
}
protected void consumeConstructorHeaderNameWithTypeParameters() {
	long selectorSourcePositions = this.identifierPositionStack[this.identifierPtr];
	int selectorSourceEnd = (int) selectorSourcePositions;
	int currentAstPtr = this.astPtr;
	super.consumeConstructorHeaderNameWithTypeParameters();
	if (this.astPtr > currentAstPtr) // if ast node was pushed on the ast stack
		this.sourceEnds.put(this.astStack[this.astPtr], selectorSourceEnd);
}
protected void consumeEnumConstantWithClassBody() {
	super.consumeEnumConstantWithClassBody();
	if ((currentToken == TokenNameCOMMA || currentToken == TokenNameSEMICOLON)
			&& astStack[astPtr] instanceof FieldDeclaration) {
		this.sourceEnds.put(this.astStack[this.astPtr], this.scanner.currentPosition - 1);
	}
}
protected void consumeEnumConstantNoClassBody() {
	super.consumeEnumConstantNoClassBody();
	if ((currentToken == TokenNameCOMMA || currentToken == TokenNameSEMICOLON)
			&& this.astStack[this.astPtr] instanceof FieldDeclaration) {
		this.sourceEnds.put(this.astStack[this.astPtr], this.scanner.currentPosition - 1);
	}
}
protected void consumeExitVariableWithInitialization() {
	// ExitVariableWithInitialization ::= $empty
	// the scanner is located after the comma or the semi-colon.
	// we want to include the comma or the semi-colon
	super.consumeExitVariableWithInitialization();
	if ((currentToken == TokenNameCOMMA || currentToken == TokenNameSEMICOLON)
			&& this.astStack[this.astPtr] instanceof FieldDeclaration) {
		this.sourceEnds.put(this.astStack[this.astPtr], this.scanner.currentPosition - 1);
	}
}
protected void consumeExitVariableWithoutInitialization() {
	// ExitVariableWithoutInitialization ::= $empty
	// do nothing by default
	super.consumeExitVariableWithoutInitialization();
	if ((currentToken == TokenNameCOMMA || currentToken == TokenNameSEMICOLON)
			&& astStack[astPtr] instanceof FieldDeclaration) {
		this.sourceEnds.put(this.astStack[this.astPtr], this.scanner.currentPosition - 1);
	}
}
/*
 *
 * INTERNAL USE-ONLY
 */
protected void consumeFieldAccess(boolean isSuperAccess) {
	// FieldAccess ::= Primary '.' 'Identifier'
	// FieldAccess ::= 'super' '.' 'Identifier'
	super.consumeFieldAccess(isSuperAccess);
	FieldReference fr = (FieldReference) expressionStack[expressionPtr];
	if (reportReferenceInfo) {
		requestor.acceptFieldReference(fr.token, fr.sourceStart);
	}
}
protected void consumeMethodHeaderName(boolean isAnnotationMethod) {
	long selectorSourcePositions = this.identifierPositionStack[this.identifierPtr];
	int selectorSourceEnd = (int) selectorSourcePositions;
	int currentAstPtr = this.astPtr;
	super.consumeMethodHeaderName(isAnnotationMethod);
	if (this.astPtr > currentAstPtr) // if ast node was pushed on the ast stack
		this.sourceEnds.put(this.astStack[this.astPtr], selectorSourceEnd);
}
protected void consumeMethodHeaderNameWithTypeParameters(boolean isAnnotationMethod) {
	long selectorSourcePositions = this.identifierPositionStack[this.identifierPtr];
	int selectorSourceEnd = (int) selectorSourcePositions;
	int currentAstPtr = this.astPtr;
	super.consumeMethodHeaderNameWithTypeParameters(isAnnotationMethod);
	if (this.astPtr > currentAstPtr) // if ast node was pushed on the ast stack
		this.sourceEnds.put(this.astStack[this.astPtr], selectorSourceEnd);
}
/*
 *
 * INTERNAL USE-ONLY
 */
protected void consumeMethodInvocationName() {
	// MethodInvocation ::= Name '(' ArgumentListopt ')'
	super.consumeMethodInvocationName();

	// when the name is only an identifier...we have a message send to "this" (implicit)
	MessageSend messageSend = (MessageSend) expressionStack[expressionPtr];
	Expression[] args = messageSend.arguments;
	if (reportReferenceInfo) {
		requestor.acceptMethodReference(
			messageSend.selector, 
			args == null ? 0 : args.length, 
			(int)(messageSend.nameSourcePosition >>> 32));
	}
}
protected void consumeMethodInvocationNameWithTypeArguments() {
	// MethodInvocation ::= Name '.' TypeArguments 'Identifier' '(' ArgumentListopt ')'
	super.consumeMethodInvocationNameWithTypeArguments();

	// when the name is only an identifier...we have a message send to "this" (implicit)
	MessageSend messageSend = (MessageSend) expressionStack[expressionPtr];
	Expression[] args = messageSend.arguments;
	if (reportReferenceInfo) {
		requestor.acceptMethodReference(
			messageSend.selector, 
			args == null ? 0 : args.length, 
			(int)(messageSend.nameSourcePosition >>> 32));
	}
}
/*
 *
 * INTERNAL USE-ONLY
 */
protected void consumeMethodInvocationPrimary() {
	super.consumeMethodInvocationPrimary();
	MessageSend messageSend = (MessageSend) expressionStack[expressionPtr];
	Expression[] args = messageSend.arguments;
	if (reportReferenceInfo) {
		requestor.acceptMethodReference(
			messageSend.selector, 
			args == null ? 0 : args.length, 
			(int)(messageSend.nameSourcePosition >>> 32));
	}
}
/*
 *
 * INTERNAL USE-ONLY
 */
protected void consumeMethodInvocationPrimaryWithTypeArguments() {
	super.consumeMethodInvocationPrimaryWithTypeArguments();
	MessageSend messageSend = (MessageSend) expressionStack[expressionPtr];
	Expression[] args = messageSend.arguments;
	if (reportReferenceInfo) {
		requestor.acceptMethodReference(
			messageSend.selector, 
			args == null ? 0 : args.length, 
			(int)(messageSend.nameSourcePosition >>> 32));
	}
}
/*
 *
 * INTERNAL USE-ONLY
 */
protected void consumeMethodInvocationSuper() {
	// MethodInvocation ::= 'super' '.' 'Identifier' '(' ArgumentListopt ')'
	super.consumeMethodInvocationSuper();
	MessageSend messageSend = (MessageSend) expressionStack[expressionPtr];
	Expression[] args = messageSend.arguments;
	if (reportReferenceInfo) {
		requestor.acceptMethodReference(
			messageSend.selector, 
			args == null ? 0 : args.length, 
			(int)(messageSend.nameSourcePosition >>> 32));
	}
}
protected void consumeMethodInvocationSuperWithTypeArguments() {
	// MethodInvocation ::= 'super' '.' TypeArguments 'Identifier' '(' ArgumentListopt ')'
	super.consumeMethodInvocationSuperWithTypeArguments();
	MessageSend messageSend = (MessageSend) expressionStack[expressionPtr];
	Expression[] args = messageSend.arguments;
	if (reportReferenceInfo) {
		requestor.acceptMethodReference(
			messageSend.selector, 
			args == null ? 0 : args.length, 
			(int)(messageSend.nameSourcePosition >>> 32));
	}
}
protected void consumeSingleStaticImportDeclarationName() {
	// SingleTypeImportDeclarationName ::= 'import' 'static' Name
	super.consumeSingleStaticImportDeclarationName();
	ImportReference impt = (ImportReference)astStack[astPtr];
	if (reportReferenceInfo) {
		// Name for static import is TypeName '.' Identifier
		// => accept unknown ref on identifier
		int length = impt.tokens.length-1;
		int start = (int) (impt.sourcePositions[length] >>> 32);
		requestor.acceptUnknownReference(impt.tokens[length], start);
		// accept type name
		if (length > 0) {
			char[][] compoundName = new char[length][];
			System.arraycopy(impt.tokens, 0, compoundName, 0, length);
			int end = (int) impt.sourcePositions[length-1];
			requestor.acceptTypeReference(compoundName, impt.sourceStart, end);
		}
	}
}
protected void consumeSingleTypeImportDeclarationName() {
	// SingleTypeImportDeclarationName ::= 'import' Name
	/* push an ImportRef build from the last name 
	stored in the identifier stack. */

	super.consumeSingleTypeImportDeclarationName();
	ImportReference impt = (ImportReference)astStack[astPtr];
	if (reportReferenceInfo) {
		requestor.acceptTypeReference(impt.tokens, impt.sourceStart, impt.sourceEnd);
	}
}
protected void consumeStaticImportOnDemandDeclarationName() {
	// TypeImportOnDemandDeclarationName ::= 'import' 'static' Name '.' '*'
	/* push an ImportRef build from the last name 
	stored in the identifier stack. */

	super.consumeStaticImportOnDemandDeclarationName();
	ImportReference impt = (ImportReference)astStack[astPtr];
	if (reportReferenceInfo) {
		requestor.acceptTypeReference(impt.tokens, impt.sourceStart, impt.sourceEnd);
	}
}
protected void consumeTypeImportOnDemandDeclarationName() {
	// TypeImportOnDemandDeclarationName ::= 'import' Name '.' '*'
	/* push an ImportRef build from the last name 
	stored in the identifier stack. */

	super.consumeTypeImportOnDemandDeclarationName();
	ImportReference impt = (ImportReference)astStack[astPtr];
	if (reportReferenceInfo) {
		requestor.acceptUnknownReference(impt.tokens, impt.sourceStart, impt.sourceEnd);
	}
}
public MethodDeclaration convertToMethodDeclaration(ConstructorDeclaration c, CompilationResult compilationResult) {
	MethodDeclaration methodDeclaration = super.convertToMethodDeclaration(c, compilationResult);
	int selectorSourceEnd = this.sourceEnds.removeKey(c);
	if (selectorSourceEnd != -1)
		this.sourceEnds.put(methodDeclaration, selectorSourceEnd);
	return methodDeclaration;
}
protected CompilationUnitDeclaration endParse(int act) {
	if (sourceType != null) {
		switch (TypeDeclaration.kind(sourceType.getModifiers())) {
			case TypeDeclaration.CLASS_DECL:
				consumeClassDeclaration();
				break;
			case TypeDeclaration.INTERFACE_DECL:
				consumeInterfaceDeclaration();
				break;
			case TypeDeclaration.ENUM_DECL:
				consumeEnumDeclaration();
				break;
			case TypeDeclaration.ANNOTATION_TYPE_DECL:
				consumeAnnotationTypeDeclaration();
				break;
		}
	}
	if (compilationUnit != null) {
		CompilationUnitDeclaration result = super.endParse(act);
		return result;
	} else {
		return null;
	}		
}
public TypeReference getTypeReference(int dim) {
	/* build a Reference on a variable that may be qualified or not
	 * This variable is a type reference and dim will be its dimensions
	 */
	int length = identifierLengthStack[identifierLengthPtr--];
	if (length < 0) { //flag for precompiled type reference on base types
		TypeReference ref = TypeReference.baseTypeReference(-length, dim);
		ref.sourceStart = intStack[intPtr--];
		if (dim == 0) {
			ref.sourceEnd = intStack[intPtr--];
		} else {
			intPtr--; // no need to use this position as it is an array
			ref.sourceEnd = endPosition;
		}
		if (reportReferenceInfo){
				requestor.acceptTypeReference(ref.getParameterizedTypeName(), ref.sourceStart, ref.sourceEnd);
		}
		return ref;
	} else {
		int numberOfIdentifiers = this.genericsIdentifiersLengthStack[this.genericsIdentifiersLengthPtr--];
		if (length != numberOfIdentifiers || this.genericsLengthStack[this.genericsLengthPtr] != 0) {
			// generic type
			TypeReference ref = getTypeReferenceForGenericType(dim, length, numberOfIdentifiers);
			if (reportReferenceInfo) {
				if (length == 1 && numberOfIdentifiers == 1) {
					ParameterizedSingleTypeReference parameterizedSingleTypeReference = (ParameterizedSingleTypeReference) ref;
					requestor.acceptTypeReference(parameterizedSingleTypeReference.token, parameterizedSingleTypeReference.sourceStart);
				} else {
					ParameterizedQualifiedTypeReference parameterizedQualifiedTypeReference = (ParameterizedQualifiedTypeReference) ref;
					requestor.acceptTypeReference(parameterizedQualifiedTypeReference.tokens, parameterizedQualifiedTypeReference.sourceStart, parameterizedQualifiedTypeReference.sourceEnd);
				}
			}
			return ref;
		} else if (length == 1) {
			// single variable reference
			this.genericsLengthPtr--; // pop the 0
			if (dim == 0) {
				SingleTypeReference ref = 
					new SingleTypeReference(
						identifierStack[identifierPtr], 
						identifierPositionStack[identifierPtr--]);
				if (reportReferenceInfo) {
					requestor.acceptTypeReference(ref.token, ref.sourceStart);
				}
				return ref;
			} else {
				ArrayTypeReference ref = 
					new ArrayTypeReference(
						identifierStack[identifierPtr], 
						dim, 
						identifierPositionStack[identifierPtr--]); 
				ref.sourceEnd = endPosition;
				if (reportReferenceInfo) {
					requestor.acceptTypeReference(ref.token, ref.sourceStart);
				}
				return ref;
			}
		} else {//Qualified variable reference
			this.genericsLengthPtr--;
			char[][] tokens = new char[length][];
			identifierPtr -= length;
			long[] positions = new long[length];
			System.arraycopy(identifierStack, identifierPtr + 1, tokens, 0, length);
			System.arraycopy(
				identifierPositionStack, 
				identifierPtr + 1, 
				positions, 
				0, 
				length); 
			if (dim == 0) {
				QualifiedTypeReference ref = new QualifiedTypeReference(tokens, positions);
				if (reportReferenceInfo) {
					requestor.acceptTypeReference(ref.tokens, ref.sourceStart, ref.sourceEnd);
				}
				return ref;
			} else {
				ArrayQualifiedTypeReference ref = 
					new ArrayQualifiedTypeReference(tokens, dim, positions); 
				ref.sourceEnd = endPosition;					
				if (reportReferenceInfo) {
					requestor.acceptTypeReference(ref.tokens, ref.sourceStart, ref.sourceEnd);
				}
				return ref;
			}
		}
	}
}
public NameReference getUnspecifiedReference() {
	/* build a (unspecified) NameReference which may be qualified*/

	int length;
	if ((length = identifierLengthStack[identifierLengthPtr--]) == 1) {
		// single variable reference
		SingleNameReference ref = 
			new SingleNameReference(
				identifierStack[identifierPtr], 
				identifierPositionStack[identifierPtr--]); 
		if (reportReferenceInfo) {
			this.addUnknownRef(ref);
		}
		return ref;
	} else {
		//Qualified variable reference
		char[][] tokens = new char[length][];
		identifierPtr -= length;
		System.arraycopy(identifierStack, identifierPtr + 1, tokens, 0, length);
		long[] positions = new long[length];
		System.arraycopy(identifierPositionStack, identifierPtr + 1, positions, 0, length);
		QualifiedNameReference ref = 
			new QualifiedNameReference(
				tokens, 
				positions,
				(int) (identifierPositionStack[identifierPtr + 1] >> 32), // sourceStart
				(int) identifierPositionStack[identifierPtr + length]); // sourceEnd
		if (reportReferenceInfo) {
			this.addUnknownRef(ref);
		}
		return ref;
	}
}
public NameReference getUnspecifiedReferenceOptimized() {
	/* build a (unspecified) NameReference which may be qualified
	The optimization occurs for qualified reference while we are
	certain in this case the last item of the qualified name is
	a field access. This optimization is IMPORTANT while it results
	that when a NameReference is build, the type checker should always
	look for that it is not a type reference */

	int length;
	if ((length = identifierLengthStack[identifierLengthPtr--]) == 1) {
		// single variable reference
		SingleNameReference ref = 
			new SingleNameReference(
				identifierStack[identifierPtr], 
				identifierPositionStack[identifierPtr--]); 
		ref.bits &= ~ASTNode.RestrictiveFlagMASK;
		ref.bits |= Binding.LOCAL | Binding.FIELD;
		if (reportReferenceInfo) {
			this.addUnknownRef(ref);
		}
		return ref;
	}

	//Qualified-variable-reference
	//In fact it is variable-reference DOT field-ref , but it would result in a type
	//conflict tha can be only reduce by making a superclass (or inetrface ) between
	//nameReference and FiledReference or putting FieldReference under NameReference
	//or else..........This optimisation is not really relevant so just leave as it is

	char[][] tokens = new char[length][];
	identifierPtr -= length;
	System.arraycopy(identifierStack, identifierPtr + 1, tokens, 0, length);
	long[] positions = new long[length];
	System.arraycopy(identifierPositionStack, identifierPtr + 1, positions, 0, length);
	QualifiedNameReference ref = 
		new QualifiedNameReference(
			tokens, 
			positions,
			(int) (identifierPositionStack[identifierPtr + 1] >> 32), 
	// sourceStart
	 (int) identifierPositionStack[identifierPtr + length]); // sourceEnd
	ref.bits &= ~ASTNode.RestrictiveFlagMASK;
	ref.bits |= Binding.LOCAL | Binding.FIELD;
	if (reportReferenceInfo) {
		this.addUnknownRef(ref);
	}
	return ref;
}
/*
 * Update the bodyStart of the corresponding parse node
 */
public void notifySourceElementRequestor(CompilationUnitDeclaration parsedUnit) {
	if (parsedUnit == null) {
		// when we parse a single type member declaration the compilation unit is null, but we still
		// want to be able to notify the requestor on the created ast node
		if (astStack[0] instanceof AbstractMethodDeclaration) {
			notifySourceElementRequestor((AbstractMethodDeclaration) astStack[0]);
			return;
		}
		return;
	}
	// range check
	boolean isInRange = 
				scanner.initialPosition <= parsedUnit.sourceStart
				&& scanner.eofPosition >= parsedUnit.sourceEnd;
	
	if (reportReferenceInfo) {
		notifyAllUnknownReferences();
	}
	// collect the top level ast nodes
	int length = 0;
	ASTNode[] nodes = null;
	if (sourceType == null){
		if (isInRange) {
			requestor.enterCompilationUnit();
		}
		ImportReference currentPackage = parsedUnit.currentPackage;
		ImportReference[] imports = parsedUnit.imports;
		TypeDeclaration[] types = parsedUnit.types;
		length = 
			(currentPackage == null ? 0 : 1) 
			+ (imports == null ? 0 : imports.length)
			+ (types == null ? 0 : types.length);
		nodes = new ASTNode[length];
		int index = 0;
		if (currentPackage != null) {
			nodes[index++] = currentPackage;
		}
		if (imports != null) {
			for (int i = 0, max = imports.length; i < max; i++) {
				nodes[index++] = imports[i];
			}
		}
		if (types != null) {
			for (int i = 0, max = types.length; i < max; i++) {
				nodes[index++] = types[i];
			}
		}
	} else {
		TypeDeclaration[] types = parsedUnit.types;
		if (types != null) {
			length = types.length;
			nodes = new ASTNode[length];
			for (int i = 0, max = types.length; i < max; i++) {
				nodes[i] = types[i];
			}
		}
	}
	
	// notify the nodes in the syntactical order
	if (nodes != null && length > 0) {
		quickSort(nodes, 0, length-1);
		for (int i=0;i<length;i++) {
			ASTNode node = nodes[i];
			if (node instanceof ImportReference) {
				ImportReference importRef = (ImportReference)node;
				if (node == parsedUnit.currentPackage) {
					notifySourceElementRequestor(importRef, true);
				} else {
					notifySourceElementRequestor(importRef, false);
				}
			} else { // instanceof TypeDeclaration
				notifySourceElementRequestor((TypeDeclaration)node, sourceType == null, null);
			}
		}
	}
	
	if (sourceType == null){
		if (isInRange) {
			requestor.exitCompilationUnit(parsedUnit.sourceEnd);
		}
	}
}

private void notifyAllUnknownReferences() {
	for (int i = 0, max = this.unknownRefsCounter; i < max; i++) {
		NameReference nameRef = this.unknownRefs[i];
		if ((nameRef.bits & Binding.VARIABLE) != 0) {
			if ((nameRef.bits & Binding.TYPE) == 0) { 
				// variable but not type
				if (nameRef instanceof SingleNameReference) { 
					// local var or field
					requestor.acceptUnknownReference(((SingleNameReference) nameRef).token, nameRef.sourceStart);
				} else {
					// QualifiedNameReference
					// The last token is a field reference and the previous tokens are a type/variable references
					char[][] tokens = ((QualifiedNameReference) nameRef).tokens;
					int tokensLength = tokens.length;
					requestor.acceptFieldReference(tokens[tokensLength - 1], nameRef.sourceEnd - tokens[tokensLength - 1].length + 1);
					char[][] typeRef = new char[tokensLength - 1][];
					System.arraycopy(tokens, 0, typeRef, 0, tokensLength - 1);
					requestor.acceptUnknownReference(typeRef, nameRef.sourceStart, nameRef.sourceEnd - tokens[tokensLength - 1].length);
				}
			} else {
				// variable or type
				if (nameRef instanceof SingleNameReference) {
					requestor.acceptUnknownReference(((SingleNameReference) nameRef).token, nameRef.sourceStart);
				} else {
					//QualifiedNameReference
					requestor.acceptUnknownReference(((QualifiedNameReference) nameRef).tokens, nameRef.sourceStart, nameRef.sourceEnd);
				}
			}
		} else if ((nameRef.bits & Binding.TYPE) != 0) {
			if (nameRef instanceof SingleNameReference) {
				requestor.acceptTypeReference(((SingleNameReference) nameRef).token, nameRef.sourceStart);
			} else {
				// it is a QualifiedNameReference
				requestor.acceptTypeReference(((QualifiedNameReference) nameRef).tokens, nameRef.sourceStart, nameRef.sourceEnd);
			}
		}
	}
}
/*
 * Update the bodyStart of the corresponding parse node
 */
public void notifySourceElementRequestor(AbstractMethodDeclaration methodDeclaration) {

	// range check
	boolean isInRange = 
				scanner.initialPosition <= methodDeclaration.declarationSourceStart
				&& scanner.eofPosition >= methodDeclaration.declarationSourceEnd;

	if (methodDeclaration.isClinit()) {
		this.visitIfNeeded(methodDeclaration);
		return;
	}

	if (methodDeclaration.isDefaultConstructor()) {
		if (reportReferenceInfo) {
			ConstructorDeclaration constructorDeclaration = (ConstructorDeclaration) methodDeclaration;
			ExplicitConstructorCall constructorCall = constructorDeclaration.constructorCall;
			if (constructorCall != null) {
				switch(constructorCall.accessMode) {
					case ExplicitConstructorCall.This :
						requestor.acceptConstructorReference(
							typeNames[nestedTypeIndex-1],
							constructorCall.arguments == null ? 0 : constructorCall.arguments.length, 
							constructorCall.sourceStart);
						break;
					case ExplicitConstructorCall.Super :
					case ExplicitConstructorCall.ImplicitSuper :					
						requestor.acceptConstructorReference(
							superTypeNames[nestedTypeIndex-1],
							constructorCall.arguments == null ? 0 : constructorCall.arguments.length, 
							constructorCall.sourceStart);
						break;
				}
			}
		}	
		return;	
	}	
	char[][] argumentTypes = null;
	char[][] argumentNames = null;
	boolean isVarArgs = false;
	Argument[] arguments = methodDeclaration.arguments;
	if (arguments != null) {
		int argumentLength = arguments.length;
		argumentTypes = new char[argumentLength][];
		argumentNames = new char[argumentLength][];
		for (int i = 0; i < argumentLength; i++) {
			argumentTypes[i] = CharOperation.concatWith(arguments[i].type.getParameterizedTypeName(), '.');
			argumentNames[i] = arguments[i].name;
		}
		isVarArgs = arguments[argumentLength-1].isVarArgs();
	}
	char[][] thrownExceptionTypes = null;
	TypeReference[] thrownExceptions = methodDeclaration.thrownExceptions;
	if (thrownExceptions != null) {
		int thrownExceptionLength = thrownExceptions.length;
		thrownExceptionTypes = new char[thrownExceptionLength][];
		for (int i = 0; i < thrownExceptionLength; i++) {
			thrownExceptionTypes[i] = 
				CharOperation.concatWith(thrownExceptions[i].getParameterizedTypeName(), '.'); 
		}
	}
	// by default no selector end position
	int selectorSourceEnd = -1;
	if (methodDeclaration.isConstructor()) {
		selectorSourceEnd = this.sourceEnds.get(methodDeclaration);
		if (isInRange){
			int currentModifiers = methodDeclaration.modifiers;
			if (isVarArgs)
				currentModifiers |= ClassFileConstants.AccVarargs;
			boolean deprecated = (currentModifiers & ClassFileConstants.AccDeprecated) != 0; // remember deprecation so as to not lose it below
			ISourceElementRequestor.MethodInfo methodInfo = new ISourceElementRequestor.MethodInfo();
			methodInfo.isConstructor = true;
			methodInfo.declarationStart = methodDeclaration.declarationSourceStart;
			methodInfo.modifiers = deprecated ? (currentModifiers & CompilerModifiers.AccJustFlag) | ClassFileConstants.AccDeprecated : currentModifiers & CompilerModifiers.AccJustFlag;
			methodInfo.name = methodDeclaration.selector;
			methodInfo.nameSourceStart = methodDeclaration.sourceStart;
			methodInfo.nameSourceEnd = selectorSourceEnd;
			methodInfo.parameterTypes = argumentTypes;
			methodInfo.parameterNames = argumentNames;
			methodInfo.exceptionTypes = thrownExceptionTypes;
			methodInfo.typeParameters = getTypeParameterInfos(methodDeclaration.typeParameters());
			methodInfo.annotationPositions = collectAnnotationPositions(methodDeclaration.annotations);
			requestor.enterConstructor(methodInfo);
		}
		if (reportReferenceInfo) {
			ConstructorDeclaration constructorDeclaration = (ConstructorDeclaration) methodDeclaration;
			ExplicitConstructorCall constructorCall = constructorDeclaration.constructorCall;
			if (constructorCall != null) {
				switch(constructorCall.accessMode) {
					case ExplicitConstructorCall.This :
						requestor.acceptConstructorReference(
							typeNames[nestedTypeIndex-1],
							constructorCall.arguments == null ? 0 : constructorCall.arguments.length, 
							constructorCall.sourceStart);
						break;
					case ExplicitConstructorCall.Super :
					case ExplicitConstructorCall.ImplicitSuper :
						requestor.acceptConstructorReference(
							superTypeNames[nestedTypeIndex-1],
							constructorCall.arguments == null ? 0 : constructorCall.arguments.length, 
							constructorCall.sourceStart);
						break;
				}
			}
		}
		this.visitIfNeeded(methodDeclaration);
		if (isInRange){
			requestor.exitConstructor(methodDeclaration.declarationSourceEnd);
		}
		return;
	}
	selectorSourceEnd = this.sourceEnds.get(methodDeclaration);
	org.aspectj.org.eclipse.jdt.internal.compiler.ast.MethodDeclaration ajmDec = new org.aspectj.org.eclipse.jdt.internal.compiler.ast.MethodDeclaration(new org.aspectj.org.eclipse.jdt.internal.compiler.CompilationResult(methodDeclaration.compilationResult.fileName, methodDeclaration.compilationResult.unitIndex, methodDeclaration.compilationResult.totalUnitsKnown, 500));
	if (ajmDec instanceof PointcutDeclaration) {
		selectorSourceEnd = methodDeclaration.sourceStart + methodDeclaration.selector.length - 1;
	}
	if (ajmDec instanceof AdviceDeclaration) {
		selectorSourceEnd = methodDeclaration.sourceStart + ((AdviceDeclaration) ajmDec).kind.getName().length() - 1; 
	}
	if (isInRange) {
		int currentModifiers = methodDeclaration.modifiers;
		if (isVarArgs)
			currentModifiers |= ClassFileConstants.AccVarargs;
		boolean deprecated = (currentModifiers & ClassFileConstants.AccDeprecated) != 0; // remember deprecation so as to not lose it below
		TypeReference returnType = methodDeclaration instanceof MethodDeclaration
			? ((MethodDeclaration) methodDeclaration).returnType
			: null;
		ISourceElementRequestor.MethodInfo methodInfo = new ISourceElementRequestor.MethodInfo();
		methodInfo.isAnnotation = methodDeclaration instanceof AnnotationMethodDeclaration;
		methodInfo.declarationStart = methodDeclaration.declarationSourceStart;
		methodInfo.modifiers = deprecated ? (currentModifiers & CompilerModifiers.AccJustFlag) | ClassFileConstants.AccDeprecated : currentModifiers & CompilerModifiers.AccJustFlag;
		methodInfo.returnType = returnType == null ? null : CharOperation.concatWith(returnType.getParameterizedTypeName(), '.');
		methodInfo.name = methodDeclaration.selector;
		methodInfo.nameSourceStart = methodDeclaration.sourceStart;
		methodInfo.nameSourceEnd = selectorSourceEnd;
		methodInfo.parameterTypes = argumentTypes;
		methodInfo.parameterNames = argumentNames;
		methodInfo.exceptionTypes = thrownExceptionTypes;
		methodInfo.typeParameters = getTypeParameterInfos(methodDeclaration.typeParameters());
		methodInfo.annotationPositions = collectAnnotationPositions(methodDeclaration.annotations);
		requestor.enterMethod(methodInfo, ajmDec);
	}		
		
	this.visitIfNeeded(methodDeclaration);

	if (isInRange) {
		if (methodDeclaration instanceof AnnotationMethodDeclaration) {
			AnnotationMethodDeclaration annotationMethodDeclaration = (AnnotationMethodDeclaration) methodDeclaration;
			Expression expression = annotationMethodDeclaration.defaultValue;
			if (expression != null) {
				requestor.exitMethod(methodDeclaration.declarationSourceEnd, expression.sourceStart, expression.sourceEnd);
				return;
			}
		} 
		requestor.exitMethod(methodDeclaration.declarationSourceEnd, -1, -1);
	}
}
private ISourceElementRequestor.TypeParameterInfo[] getTypeParameterInfos(TypeParameter[] typeParameters) {
	if (typeParameters == null) return null;
	int typeParametersLength = typeParameters.length;
	ISourceElementRequestor.TypeParameterInfo[] result = new ISourceElementRequestor.TypeParameterInfo[typeParametersLength];
	for (int i = 0; i < typeParametersLength; i++) {
		TypeParameter typeParameter = typeParameters[i];
		TypeReference firstBound = typeParameter.type;
		TypeReference[] otherBounds = typeParameter.bounds;
		char[][] typeParameterBounds = null;
		if (firstBound != null) {
			if (otherBounds != null) {
				int otherBoundsLength = otherBounds.length;
				char[][] boundNames = new char[otherBoundsLength+1][];
				boundNames[0] = CharOperation.concatWith(firstBound.getParameterizedTypeName(), '.');
				for (int j = 0; j < otherBoundsLength; j++) {
					boundNames[j+1] = 
						CharOperation.concatWith(otherBounds[j].getParameterizedTypeName(), '.'); 
				}
				typeParameterBounds = boundNames;
			} else {
				typeParameterBounds = new char[][] { CharOperation.concatWith(firstBound.getParameterizedTypeName(), '.')};
			}
		} else {
			typeParameterBounds = CharOperation.NO_CHAR_CHAR;
		}
		ISourceElementRequestor.TypeParameterInfo typeParameterInfo = new ISourceElementRequestor.TypeParameterInfo();
		typeParameterInfo.declarationStart = typeParameter.declarationSourceStart;
		typeParameterInfo.declarationEnd = typeParameter.declarationSourceEnd;
		typeParameterInfo.name = typeParameter.name;
		typeParameterInfo.nameSourceStart = typeParameter.sourceStart;
		typeParameterInfo.nameSourceEnd = typeParameter.sourceEnd;
		typeParameterInfo.bounds = typeParameterBounds;
		result[i] = typeParameterInfo;
	}
	return result;
}

/*
* Update the bodyStart of the corresponding parse node
*/
public void notifySourceElementRequestor(FieldDeclaration fieldDeclaration, TypeDeclaration declaringType) {
	
	// range check
	boolean isInRange = 
				scanner.initialPosition <= fieldDeclaration.declarationSourceStart
				&& scanner.eofPosition >= fieldDeclaration.declarationSourceEnd;

	switch(fieldDeclaration.getKind()) {
		case AbstractVariableDeclaration.ENUM_CONSTANT:
			// accept constructor reference for enum constant
			if (fieldDeclaration.initialization instanceof AllocationExpression) {
				AllocationExpression alloc = (AllocationExpression) fieldDeclaration.initialization;
				requestor.acceptConstructorReference(
					declaringType.name,
					alloc.arguments == null ? 0 : alloc.arguments.length, 
					alloc.sourceStart);
			}
			// fall through next case
		case AbstractVariableDeclaration.FIELD:
			int fieldEndPosition = this.sourceEnds.get(fieldDeclaration);
			if (fieldEndPosition == -1) {
				// use the declaration source end by default
				fieldEndPosition = fieldDeclaration.declarationSourceEnd;
			}
			if (isInRange) {
				int currentModifiers = fieldDeclaration.modifiers;
				boolean deprecated = (currentModifiers & ClassFileConstants.AccDeprecated) != 0; // remember deprecation so as to not lose it below
				char[] typeName = null;
				if (fieldDeclaration.type == null) {
					// enum constant
					typeName = declaringType.name;
					currentModifiers |= ClassFileConstants.AccEnum;
				} else {
					// regular field
					typeName = CharOperation.concatWith(fieldDeclaration.type.getParameterizedTypeName(), '.');
				}
				ISourceElementRequestor.FieldInfo fieldInfo = new ISourceElementRequestor.FieldInfo();
				fieldInfo.declarationStart = fieldDeclaration.declarationSourceStart;
				fieldInfo.name = fieldDeclaration.name;
				fieldInfo.modifiers = deprecated ? (currentModifiers & CompilerModifiers.AccJustFlag) | ClassFileConstants.AccDeprecated : currentModifiers & CompilerModifiers.AccJustFlag;
				fieldInfo.type = typeName;
				fieldInfo.nameSourceStart = fieldDeclaration.sourceStart;
				fieldInfo.nameSourceEnd = fieldDeclaration.sourceEnd;
				fieldInfo.annotationPositions = collectAnnotationPositions(fieldDeclaration.annotations);
				requestor.enterField(fieldInfo);
			}
			this.visitIfNeeded(fieldDeclaration, declaringType);
			if (isInRange){
				requestor.exitField(
					// filter out initializations that are not a constant (simple check)
					(fieldDeclaration.initialization == null 
							|| fieldDeclaration.initialization instanceof ArrayInitializer
							|| fieldDeclaration.initialization instanceof AllocationExpression
							|| fieldDeclaration.initialization instanceof ArrayAllocationExpression
							|| fieldDeclaration.initialization instanceof Assignment
							|| fieldDeclaration.initialization instanceof ClassLiteralAccess
							|| fieldDeclaration.initialization instanceof MessageSend
							|| fieldDeclaration.initialization instanceof ArrayReference
							|| fieldDeclaration.initialization instanceof ThisReference) ? 
						-1 :  
						fieldDeclaration.initialization.sourceStart, 
					fieldEndPosition,
					fieldDeclaration.declarationSourceEnd);
			}
			break;
		case AbstractVariableDeclaration.INITIALIZER:
			if (isInRange){
				requestor.enterInitializer(
					fieldDeclaration.declarationSourceStart,
					fieldDeclaration.modifiers); 
			}
			this.visitIfNeeded((Initializer)fieldDeclaration);
			if (isInRange){
				requestor.exitInitializer(fieldDeclaration.declarationSourceEnd);
			}
			break;
	}
}
public void notifySourceElementRequestor(
	ImportReference importReference, 
	boolean isPackage) {
	if (isPackage) {
		requestor.acceptPackage(
			importReference.declarationSourceStart, 
			importReference.declarationSourceEnd, 
			CharOperation.concatWith(importReference.getImportName(), '.')); 
	} else {
		requestor.acceptImport(
			importReference.declarationSourceStart, 
			importReference.declarationSourceEnd, 
			CharOperation.concatWith(importReference.getImportName(), '.'), 
			importReference.onDemand,
			importReference.modifiers); 
	}
}
public void notifySourceElementRequestor(TypeDeclaration typeDeclaration, boolean notifyTypePresence, TypeDeclaration declaringType) {
	
    //	added AspectJ Luzius
	boolean isAspect = false;
	org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeDeclaration ajtypeDeclaration = new org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeDeclaration(new org.aspectj.org.eclipse.jdt.internal.compiler.CompilationResult(typeDeclaration.compilationResult.fileName, typeDeclaration.compilationResult.unitIndex, typeDeclaration.compilationResult.totalUnitsKnown, 500));
	if (ajtypeDeclaration instanceof AspectDeclaration)
		isAspect = true;
	
	// range check
	boolean isInRange = 
		scanner.initialPosition <= typeDeclaration.declarationSourceStart
		&& scanner.eofPosition >= typeDeclaration.declarationSourceEnd;
	
	FieldDeclaration[] fields = typeDeclaration.fields;
	AbstractMethodDeclaration[] methods = typeDeclaration.methods;
	TypeDeclaration[] memberTypes = typeDeclaration.memberTypes;
	int fieldCounter = fields == null ? 0 : fields.length;
	int methodCounter = methods == null ? 0 : methods.length;
	int memberTypeCounter = memberTypes == null ? 0 : memberTypes.length;
	int fieldIndex = 0;
	int methodIndex = 0;
	int memberTypeIndex = 0;
	
	if (notifyTypePresence){
		char[][] interfaceNames = null;
		int superInterfacesLength = 0;
		TypeReference[] superInterfaces = typeDeclaration.superInterfaces;
		if (superInterfaces != null) {
			superInterfacesLength = superInterfaces.length;
			interfaceNames = new char[superInterfacesLength][];
		} else {
			if ((typeDeclaration.bits & ASTNode.IsAnonymousType) != 0) {
				// see PR 3442
				QualifiedAllocationExpression alloc = typeDeclaration.allocation;
				if (alloc != null && alloc.type != null) {
					superInterfaces = new TypeReference[] { alloc.type};
					superInterfacesLength = 1;
					interfaceNames = new char[1][];
				}
			}
		}
		if (superInterfaces != null) {
			for (int i = 0; i < superInterfacesLength; i++) {
				interfaceNames[i] = 
					CharOperation.concatWith(superInterfaces[i].getParameterizedTypeName(), '.'); 
			}
		}
		int kind = TypeDeclaration.kind(typeDeclaration.modifiers);
		char[] implicitSuperclassName = TypeConstants.CharArray_JAVA_LANG_OBJECT;
		if (isInRange) {
			int currentModifiers = typeDeclaration.modifiers;
			boolean deprecated = (currentModifiers & CompilerModifiers.AccDeprecated) != 0; // remember deprecation so as to not lose it below
			boolean isEnumInit = typeDeclaration.allocation != null && typeDeclaration.allocation.enumConstant != null;
			char[] superclassName;
			if (isEnumInit) {
				currentModifiers |= CompilerModifiers.AccEnum;
				superclassName = declaringType.name;
			} else {
				TypeReference superclass = typeDeclaration.superclass;
				superclassName = superclass != null ? CharOperation.concatWith(superclass.getParameterizedTypeName(), '.') : null;
			}
			ISourceElementRequestor.TypeInfo typeInfo = new ISourceElementRequestor.TypeInfo();
			typeInfo.declarationStart = typeDeclaration.declarationSourceStart;
			typeInfo.modifiers = deprecated ? (currentModifiers & CompilerModifiers.AccJustFlag) | CompilerModifiers.AccDeprecated : currentModifiers & CompilerModifiers.AccJustFlag;
			typeInfo.name = typeDeclaration.name;
			typeInfo.nameSourceStart = typeDeclaration.sourceStart;
			typeInfo.nameSourceEnd = sourceEnd(typeDeclaration);
			typeInfo.superclass = superclassName;
			typeInfo.superinterfaces = interfaceNames;
			typeInfo.typeParameters = getTypeParameterInfos(typeDeclaration.typeParameters);
			typeInfo.annotationPositions = collectAnnotationPositions(typeDeclaration.annotations);
			requestor.enterType(typeInfo,isAspect);
			switch (kind) {
				case TypeDeclaration.CLASS_DECL:
					if (superclassName != null)
						implicitSuperclassName = superclassName;
					break;
				case TypeDeclaration.INTERFACE_DECL:
					implicitSuperclassName = TypeConstants.CharArray_JAVA_LANG_OBJECT;
					break;
				case TypeDeclaration.ENUM_DECL:
					implicitSuperclassName = TypeConstants.CharArray_JAVA_LANG_ENUM;
					break;
				case TypeDeclaration.ANNOTATION_TYPE_DECL:
					implicitSuperclassName = TypeConstants.CharArray_JAVA_LANG_ANNOTATION_ANNOTATION;
					break;
				}
		}
		if (this.nestedTypeIndex == this.typeNames.length) {
			// need a resize
			System.arraycopy(this.typeNames, 0, (this.typeNames = new char[this.nestedTypeIndex * 2][]), 0, this.nestedTypeIndex);
			System.arraycopy(this.superTypeNames, 0, (this.superTypeNames = new char[this.nestedTypeIndex * 2][]), 0, this.nestedTypeIndex);
		}
		this.typeNames[this.nestedTypeIndex] = typeDeclaration.name;
		this.superTypeNames[this.nestedTypeIndex++] = implicitSuperclassName;
	}
	while ((fieldIndex < fieldCounter)
			|| (memberTypeIndex < memberTypeCounter)
			|| (methodIndex < methodCounter)) {
		FieldDeclaration nextFieldDeclaration = null;
		AbstractMethodDeclaration nextMethodDeclaration = null;
		TypeDeclaration nextMemberDeclaration = null;
		
		int position = Integer.MAX_VALUE;
		int nextDeclarationType = -1;
		if (fieldIndex < fieldCounter) {
			nextFieldDeclaration = fields[fieldIndex];
			if (nextFieldDeclaration.declarationSourceStart < position) {
				position = nextFieldDeclaration.declarationSourceStart;
				nextDeclarationType = 0; // FIELD
			}
		}
		if (methodIndex < methodCounter) {
			nextMethodDeclaration = methods[methodIndex];
			if (nextMethodDeclaration.declarationSourceStart < position) {
				position = nextMethodDeclaration.declarationSourceStart;
				nextDeclarationType = 1; // METHOD
			}
		}
		if (memberTypeIndex < memberTypeCounter) {
			nextMemberDeclaration = memberTypes[memberTypeIndex];
			if (nextMemberDeclaration.declarationSourceStart < position) {
				position = nextMemberDeclaration.declarationSourceStart;
				nextDeclarationType = 2; // MEMBER
			}
		}
		switch (nextDeclarationType) {
			case 0 :
				fieldIndex++;
				notifySourceElementRequestor(nextFieldDeclaration, typeDeclaration);
				break;
			case 1 :
				methodIndex++;
				notifySourceElementRequestor(nextMethodDeclaration);
				break;
			case 2 :
				memberTypeIndex++;
				notifySourceElementRequestor(nextMemberDeclaration, true, null);
		}
	}
	if (notifyTypePresence){
		if (isInRange){
			requestor.exitType(typeDeclaration.declarationSourceEnd);
		}
		nestedTypeIndex--;
	}
}
private int sourceEnd(TypeDeclaration typeDeclaration) {
	if ((typeDeclaration.bits & ASTNode.IsAnonymousType) != 0) {
		QualifiedAllocationExpression allocation = typeDeclaration.allocation;
		if (allocation.type == null) // case of enum constant body
			return typeDeclaration.sourceEnd;
		return allocation.type.sourceEnd;
	} else {
		return typeDeclaration.sourceEnd;
	}
}
public void parseCompilationUnit(
	ICompilationUnit unit, 
	int start, 
	int end, 
	boolean fullParse) {

	this.reportReferenceInfo = fullParse;
	boolean old = diet;
	if (fullParse) {
		unknownRefs = new NameReference[10];
		unknownRefsCounter = 0;
	}
	
	try {
		diet = true;
		CompilationResult compilationUnitResult = new CompilationResult(unit, 0, 0, this.options.maxProblemsPerUnit);
		CompilationUnitDeclaration parsedUnit = parse(unit, compilationUnitResult, start, end);
		if (scanner.recordLineSeparator) {
			requestor.acceptLineSeparatorPositions(compilationUnitResult.lineSeparatorPositions);
		}
		if (this.localDeclarationVisitor != null || fullParse){
			diet = false;
			this.getMethodBodies(parsedUnit);
		}		
		this.scanner.resetTo(start, end);
		notifySourceElementRequestor(parsedUnit);
	} catch (AbortCompilation e) {
		// ignore this exception
	} finally {
		diet = old;
	}
}
public CompilationUnitDeclaration parseCompilationUnit(
	ICompilationUnit unit, 
	boolean fullParse) {
		
	boolean old = diet;
	if (fullParse) {
		unknownRefs = new NameReference[10];
		unknownRefsCounter = 0;
	}

	try {
		diet = true;
		this.reportReferenceInfo = fullParse;
		CompilationResult compilationUnitResult = new CompilationResult(unit, 0, 0, this.options.maxProblemsPerUnit);
		CompilationUnitDeclaration parsedUnit = parse(unit, compilationUnitResult);
		if (scanner.recordLineSeparator) {
			requestor.acceptLineSeparatorPositions(compilationUnitResult.lineSeparatorPositions);
		}
		int initialStart = this.scanner.initialPosition;
		int initialEnd = this.scanner.eofPosition;
		if (this.localDeclarationVisitor != null || fullParse){
			diet = false;
			this.getMethodBodies(parsedUnit);
		}
		this.scanner.resetTo(initialStart, initialEnd);
		notifySourceElementRequestor(parsedUnit);
		return parsedUnit;
	} catch (AbortCompilation e) {
		// ignore this exception
	} finally {
		diet = old;
	}
	return null;
}
public void parseTypeMemberDeclarations(
	ISourceType type, 
	ICompilationUnit sourceUnit, 
	int start, 
	int end, 
	boolean needReferenceInfo) {
	boolean old = diet;
	if (needReferenceInfo) {
		unknownRefs = new NameReference[10];
		unknownRefsCounter = 0;
	}
	
	CompilationResult compilationUnitResult = 
		new CompilationResult(sourceUnit, 0, 0, this.options.maxProblemsPerUnit); 
	try {
		diet = !needReferenceInfo;
		reportReferenceInfo = needReferenceInfo;
		CompilationUnitDeclaration unit = 
			SourceTypeConverter.buildCompilationUnit(
				new ISourceType[]{type}, 
				// no need for field and methods
				// no need for member types
				// no need for field initialization
				SourceTypeConverter.NONE,
				problemReporter(), 
				compilationUnitResult); 
		if ((unit == null) || (unit.types == null) || (unit.types.length != 1))
			return;
		this.sourceType = type;
		try {
			/* automaton initialization */
			initialize();
			goForClassBodyDeclarations();
			/* scanner initialization */
			scanner.setSource(sourceUnit.getContents());
			scanner.resetTo(start, end);
			/* unit creation */
			referenceContext = compilationUnit = unit;
			/* initialize the astStacl */
			// the compilationUnitDeclaration should contain exactly one type
			pushOnAstStack(unit.types[0]);
			/* run automaton */
			parse();
			notifySourceElementRequestor(unit);
		} finally {
			unit = compilationUnit;
			compilationUnit = null; // reset parser
		}
	} catch (AbortCompilation e) {
		// ignore this exception
	} finally {
		if (scanner.recordLineSeparator) {
			requestor.acceptLineSeparatorPositions(compilationUnitResult.lineSeparatorPositions);
		}
		diet = old;
	}
}

public void parseTypeMemberDeclarations(
	char[] contents, 
	int start, 
	int end) {

	boolean old = diet;
	
	try {
		diet = true;

		/* automaton initialization */
		initialize();
		goForClassBodyDeclarations();
		/* scanner initialization */
		scanner.setSource(contents);
		scanner.recordLineSeparator = false;
		scanner.taskTags = null;
		scanner.taskPriorities = null;
		scanner.resetTo(start, end);

		/* unit creation */
		referenceContext = null;

		/* initialize the astStacl */
		// the compilationUnitDeclaration should contain exactly one type
		/* run automaton */
		parse();
		notifySourceElementRequestor((CompilationUnitDeclaration)null);
	} catch (AbortCompilation e) {
		// ignore this exception
	} finally {
		diet = old;
	}
}
/*
 * Sort the given ast nodes by their positions.
 */
private static void quickSort(ASTNode[] sortedCollection, int left, int right) {
	int original_left = left;
	int original_right = right;
	ASTNode mid = sortedCollection[ (left + right) / 2];
	do {
		while (sortedCollection[left].sourceStart < mid.sourceStart) {
			left++;
		}
		while (mid.sourceStart < sortedCollection[right].sourceStart) {
			right--;
		}
		if (left <= right) {
			ASTNode tmp = sortedCollection[left];
			sortedCollection[left] = sortedCollection[right];
			sortedCollection[right] = tmp;
			left++;
			right--;
		}
	} while (left <= right);
	if (original_left < right) {
		quickSort(sortedCollection, original_left, right);
	}
	if (left < original_right) {
		quickSort(sortedCollection, left, original_right);
	}
}
public void addUnknownRef(NameReference nameRef) {
	if (this.unknownRefs.length == this.unknownRefsCounter) {
		// resize
		System.arraycopy(
			this.unknownRefs,
			0,
			(this.unknownRefs = new NameReference[this.unknownRefsCounter * 2]),
			0,
			this.unknownRefsCounter);
	}
	this.unknownRefs[this.unknownRefsCounter++] = nameRef;
}

private void visitIfNeeded(AbstractMethodDeclaration method) {
	if (this.localDeclarationVisitor != null 
		&& (method.bits & ASTNode.HasLocalType) != 0) {
			if (method instanceof ConstructorDeclaration) {
				ConstructorDeclaration constructorDeclaration = (ConstructorDeclaration) method;
				if (constructorDeclaration.constructorCall != null) {
					constructorDeclaration.constructorCall.traverse(this.localDeclarationVisitor, method.scope);
				}
			}
			if (method.statements != null) {
				int statementsLength = method.statements.length;
				for (int i = 0; i < statementsLength; i++)
					method.statements[i].traverse(this.localDeclarationVisitor, method.scope);
			}
	}
}

private void visitIfNeeded(FieldDeclaration field, TypeDeclaration declaringType) {
	if (this.localDeclarationVisitor != null 
		&& (field.bits & ASTNode.HasLocalType) != 0) {
			if (field.initialization != null) {
				try {
					this.localDeclarationVisitor.pushDeclaringType(declaringType);
					field.initialization.traverse(this.localDeclarationVisitor, (MethodScope) null);
				} finally {
					this.localDeclarationVisitor.popDeclaringType();
				}
			}
	}
}

private void visitIfNeeded(Initializer initializer) {
	if (this.localDeclarationVisitor != null 
		&& (initializer.bits & ASTNode.HasLocalType) != 0) {
			if (initializer.block != null) {
				initializer.block.traverse(this.localDeclarationVisitor, null);
			}
	}
}
}