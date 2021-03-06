/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import org.truffleruby.Layouts;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

@GenerateUncached
public abstract class IsTaintedNode extends RubyBaseNode {

    public static IsTaintedNode create() {
        return IsTaintedNodeGen.create();
    }

    public abstract boolean executeIsTainted(Object object);

    @Specialization
    protected boolean isTainted(boolean object) {
        return false;
    }

    @Specialization
    protected boolean isTainted(int object) {
        return false;
    }

    @Specialization
    protected boolean isTainted(long object) {
        return false;
    }

    @Specialization
    protected boolean isTainted(double object) {
        return false;
    }

    @Specialization
    protected boolean isTainted(Nil object) {
        return false;
    }

    @Specialization
    protected boolean isTaintedSymbol(RubySymbol object) {
        return false;
    }

    @Specialization
    protected boolean isTainted(DynamicObject object,
            @Cached ReadObjectFieldNode readTaintedNode) {
        return (boolean) readTaintedNode.execute(object, Layouts.TAINTED_IDENTIFIER, false);
    }

    @Specialization(guards = "isForeignObject(object)")
    protected boolean isTainted(Object object) {
        return false;
    }
}
