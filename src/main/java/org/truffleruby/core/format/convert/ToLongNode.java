/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.convert;

import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.exceptions.CantConvertException;
import org.truffleruby.core.format.exceptions.NoImplicitConversionException;
import org.truffleruby.core.numeric.BigIntegerOps;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;

@NodeChild("value")
public abstract class ToLongNode extends FormatNode {

    private final boolean errorIfNeedsConversion;

    @Child private CallDispatchHeadNode toIntNode;
    @Child private ToLongNode redoNode;

    public ToLongNode(boolean errorIfNeedsConversion) {
        this.errorIfNeedsConversion = errorIfNeedsConversion;
    }

    public abstract long executeToLong(VirtualFrame frame, Object object);

    @Specialization
    protected long toLong(boolean object) {
        throw new NoImplicitConversionException(object, "Integer");
    }

    @Specialization
    protected long toLong(int object) {
        return object;
    }

    @Specialization
    protected long toLong(long object) {
        return object;
    }

    @Specialization(guards = "isRubyBignum(object)")
    protected long toLong(DynamicObject object) {
        // A truncated value is exactly what we want
        return BigIntegerOps.longValue(object);
    }

    @Specialization(guards = "isNil(nil)")
    protected long toLongNil(Object nil) {
        throw new NoImplicitConversionException(nil, "Integer");
    }

    @Specialization(
            guards = {
                    "!isBoolean(object)",
                    "!isInteger(object)",
                    "!isLong(object)",
                    "!isBigInteger(object)",
                    "!isRubyBignum(object)",
                    "!isNil(object)" })
    protected long toLong(VirtualFrame frame, Object object) {
        if (errorIfNeedsConversion) {
            throw new CantConvertException("can't convert Object to Integer");
        }

        if (toIntNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toIntNode = insert(CallDispatchHeadNode.createReturnMissing());
        }

        final Object value = toIntNode.call(object, "to_int");

        if (redoNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            redoNode = insert(ToLongNodeGen.create(true, null));
        }

        return redoNode.executeToLong(frame, value);
    }

}
