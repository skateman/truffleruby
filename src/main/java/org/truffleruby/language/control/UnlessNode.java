/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.cast.BooleanCastNodeGen;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;

public class UnlessNode extends RubyContextSourceNode {

    @Child private BooleanCastNode condition;
    @Child private RubyNode thenBody;

    private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

    public UnlessNode(RubyNode condition, RubyNode thenBody) {
        this.condition = BooleanCastNodeGen.create(condition);
        this.thenBody = thenBody;
    }

    public UnlessNode(BooleanCastNode condition, RubyNode thenBody) {
        this.condition = condition;
        this.thenBody = thenBody;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (!conditionProfile.profile(condition.executeBoolean(frame))) {
            return thenBody.execute(frame);
        } else {
            return nil;
        }
    }

    @Override
    public boolean canSubsumeFollowing() {
        return !thenBody.isContinuable();
    }

    @Override
    public RubyNode subsumeFollowing(RubyNode following) {
        RubyNode newNode = new IfElseNode(condition, following, thenBody);
        SourceSection source = getSourceSection();
        if (source != null) {
            newNode.unsafeSetSourceSection(source);
        }
        return newNode;
    }

    @Override
    public RubyNode simplifyAsTailExpression() {
        final UnlessNode unlessNode = new UnlessNode(condition, thenBody.simplifyAsTailExpression());
        SourceSection source = getSourceSection();
        if (source != null) {
            unlessNode.unsafeSetSourceSection(source);
        }
        return unlessNode;
    }
}
