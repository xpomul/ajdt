/*******************************************************************************
 * Copyright (c) 2000, 2019 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.aspectj.org.eclipse.jdt.internal.compiler.parser;

/**
 * IMPORTANT NOTE: These constants are dedicated to the internal Scanner implementation.
 * It is mirrored in org.aspectj.org.eclipse.jdt.core.compiler public package where it is API.
 * The mirror implementation is using the backward compatible ITerminalSymbols constant
 * definitions (stable with 2.0), whereas the internal implementation uses TerminalTokens
 * which constant values reflect the latest parser generation state.
 */
/**
 * Maps each terminal symbol in the java-grammar into a unique integer.
 * This integer is used to represent the terminal when computing a parsing action.
 *
 * Disclaimer : These constant values are generated automatically using a Java
 * grammar, therefore their actual values are subject to change if new keywords
 * were added to the language (for instance, 'assert' is a keyword in 1.4).
 */
public interface TerminalTokens {

	// special tokens not part of grammar - not autogenerated
	int TokenNameNotAToken = 0,
							TokenNameWHITESPACE = 1000,
							TokenNameCOMMENT_LINE = 1001,
							TokenNameCOMMENT_BLOCK = 1002,
							TokenNameCOMMENT_JAVADOC = 1003;

	// AspectJ: Tokens taken from javasym.java, generated by jikespg according to
	// https://www.eclipse.org/jdt/core/howto/generate%20parser/generateParser.html
	//
	// TODO Every time when updating tokens, make sure to rename
	//   - TokenName$eof -> TokenNameEOF
	//   - TokenName$error -> TokenNameERROR
	//   - TokenNamenon-sealed -> TokenNamenon_sealed
	int
		TokenNameIdentifier = 12,
		TokenNameabstract = 48,
		TokenNameassert = 86,
		TokenNameboolean = 119,
		TokenNamebreak = 87,
		TokenNamebyte = 120,
		TokenNamecase = 95,
		TokenNamecatch = 97,
		TokenNamechar = 121,
		TokenNameclass = 75,
		TokenNamecontinue = 88,
		TokenNameconst = 140,
		TokenNamedefault = 103,
		TokenNamedo = 89,
		TokenNamedouble = 122,
		TokenNameelse = 104,
		TokenNameenum = 82,
		TokenNameextends = 96,
		TokenNamefalse = 59,
		TokenNamefinal = 49,
		TokenNamefinally = 100,
		TokenNamefloat = 123,
		TokenNamefor = 90,
		TokenNamegoto = 141,
		TokenNameif = 91,
		TokenNameimplements = 106,
		TokenNameimport = 98,
		TokenNameinstanceof = 17,
		TokenNameint = 124,
		TokenNameinterface = 79,
		TokenNamelong = 125,
		TokenNamenative = 50,
		TokenNamenew = 42,
		TokenNamenon_sealed = 51,
		TokenNamenull = 60,
		TokenNamepackage = 94,
		TokenNameprivate = 52,
		TokenNameprotected = 53,
		TokenNamepublic = 54,
		TokenNamereturn = 92,
		TokenNameshort = 126,
		TokenNamestatic = 43,
		TokenNamestrictfp = 55,
		TokenNamesuper = 44,
		TokenNameswitch = 70,
		TokenNamesynchronized = 46,
		TokenNamethis = 45,
		TokenNamethrow = 83,
		TokenNamethrows = 101,
		TokenNametransient = 56,
		TokenNametrue = 61,
		TokenNametry = 93,
		TokenNamevoid = 127,
		TokenNamevolatile = 57,
		TokenNamewhile = 84,
		TokenNamemodule = 128,
		TokenNameopen = 129,
		TokenNamerequires = 130,
		TokenNametransitive = 136,
		TokenNameexports = 131,
		TokenNameopens = 132,
		TokenNameto = 138,
		TokenNameuses = 133,
		TokenNameprovides = 134,
		TokenNamewith = 139,
		TokenNameaspect = 25,
		TokenNamepointcut = 29,
		TokenNamearound = 33,
		TokenNamebefore = 30,
		TokenNameafter = 31,
		TokenNamedeclare = 32,
		TokenNameprivileged = 28,
		TokenNameIntegerLiteral = 62,
		TokenNameLongLiteral = 63,
		TokenNameFloatingPointLiteral = 64,
		TokenNameDoubleLiteral = 65,
		TokenNameCharacterLiteral = 66,
		TokenNameStringLiteral = 67,
		TokenNameTextBlock = 68,
		TokenNamePLUS_PLUS = 3,
		TokenNameMINUS_MINUS = 4,
		TokenNameEQUAL_EQUAL = 23,
		TokenNameLESS_EQUAL = 18,
		TokenNameGREATER_EQUAL = 19,
		TokenNameNOT_EQUAL = 20,
		TokenNameLEFT_SHIFT = 21,
		TokenNameRIGHT_SHIFT = 13,
		TokenNameUNSIGNED_RIGHT_SHIFT = 16,
		TokenNamePLUS_EQUAL = 107,
		TokenNameMINUS_EQUAL = 108,
		TokenNameMULTIPLY_EQUAL = 109,
		TokenNameDIVIDE_EQUAL = 110,
		TokenNameAND_EQUAL = 111,
		TokenNameOR_EQUAL = 112,
		TokenNameXOR_EQUAL = 113,
		TokenNameREMAINDER_EQUAL = 114,
		TokenNameLEFT_SHIFT_EQUAL = 115,
		TokenNameRIGHT_SHIFT_EQUAL = 116,
		TokenNameUNSIGNED_RIGHT_SHIFT_EQUAL = 117,
		TokenNameOR_OR = 38,
		TokenNameAND_AND = 37,
		TokenNamePLUS = 2,
		TokenNameMINUS = 6,
		TokenNameNOT = 72,
		TokenNameREMAINDER = 10,
		TokenNameXOR = 34,
		TokenNameAND = 22,
		TokenNameMULTIPLY = 8,
		TokenNameOR = 36,
		TokenNameTWIDDLE = 76,
		TokenNameDIVIDE = 11,
		TokenNameGREATER = 14,
		TokenNameLESS = 7,
		TokenNameLPAREN = 15,
		TokenNameRPAREN = 26,
		TokenNameLBRACE = 58,
		TokenNameRBRACE = 40,
		TokenNameLBRACKET = 5,
		TokenNameRBRACKET = 74,
		TokenNameSEMICOLON = 27,
		TokenNameQUESTION = 35,
		TokenNameCOLON = 69,
		TokenNameCOMMA = 39,
		TokenNameDOT = 1,
		TokenNameEQUAL = 78,
		TokenNameAT = 41,
		TokenNameELLIPSIS = 102,
		TokenNameARROW = 118,
		TokenNameCOLON_COLON = 9,
		TokenNameBeginLambda = 71,
		TokenNameBeginIntersectionCast = 77,
		TokenNameBeginTypeArguments = 99,
		TokenNameElidedSemicolonAndRightBrace = 80,
		TokenNameAT308 = 24,
		TokenNameAT308DOTDOTDOT = 137,
		TokenNameBeginCaseExpr = 81,
		TokenNameRestrictedIdentifierYield = 105,
		TokenNameRestrictedIdentifierrecord = 85,
		TokenNameRestrictedIdentifiersealed = 47,
		TokenNameRestrictedIdentifierpermits = 135,
		TokenNameEOF = 73,
		TokenNameERROR = 142;
}
