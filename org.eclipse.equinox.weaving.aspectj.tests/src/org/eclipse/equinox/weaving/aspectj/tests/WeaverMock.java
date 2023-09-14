/*******************************************************************************
 * Copyright (c) 2023 Stefan Winkler and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Stefan Winkler               initial implementation
 *******************************************************************************/
package org.eclipse.equinox.weaving.aspectj.tests;

import org.aspectj.weaver.bcel.BcelWeaver;
import org.aspectj.weaver.bcel.BcelWorld;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import org.aspectj.weaver.IClassFileProvider;
import org.aspectj.weaver.bcel.BcelObjectType;
import org.aspectj.weaver.bcel.UnwovenClassFile;

/**
 * Mock of the BcelWeaver that does not actually weave, but only causes the effect of weaving a hypothetical
 * class "my.foo.MyClass" that has a base class "my.foo.MyBaseClass". 
 * 
 * See https://github.com/eclipse-aspectj/ajdt/issues/45 for the explanation.
 * 
 * @author Stefan Winkler <stefan@winklerweb.net>
 */
public class WeaverMock extends BcelWeaver {
    
    /**
     * Constructor. We use an uninitialized BcelWorld as parameter, which is sufficient for our test case.
     */
    public WeaverMock() {
        super(new BcelWorld());
    }

    @Override
    public Collection<String> weave(IClassFileProvider input) throws IOException {
        // We simulate what happens to the generatedClass field when we are weaving a class with a base class;
        // and aspects are applied to both the parent and the base class:

        // First the target class is woven and offered to the acceptor:        
        input.getRequestor().acceptResult(new UnwovenClassFile("MyClass.java", "my.foo.MyClass", new byte[0]));
        
        // then the generated closure classes are offered:
        input.getRequestor().acceptResult(new UnwovenClassFile("MyClass.java", "my.foo.MyClass$AjcClosure1", new byte[0]));
        input.getRequestor().acceptResult(new UnwovenClassFile("MyClass.java", "my.foo.MyClass$AjcClosure3", new byte[0]));

        // then, the target class is defined and consequently, the base class is loaded and woven,
        // which causes the base class and its aspect closure to be added:
        input.getRequestor().acceptResult(new UnwovenClassFile("MyBaseClass.java", "my.foo.MyBaseClass", new byte[0]));
        input.getRequestor().acceptResult(new UnwovenClassFile("MyBaseClass.java", "my.foo.MyBaseClass$AjcClosure1", new byte[0]));

        // the caller does not care about the return value, so we just return the empty list
        return Collections.emptyList();
    }
}
