/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.core.hash.Entry;
import org.truffleruby.core.queue.SizedQueue;
import org.truffleruby.core.queue.UnsizedQueue;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.arguments.RubyArguments;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;

public abstract class ObjectGraph {

    public static Set<Object> newObjectSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    @TruffleBoundary
    public static Set<Object> stopAndGetAllObjects(Node currentNode, final RubyContext context) {
        context.getMarkingService().queueMarking();
        final Set<Object> visited = newObjectSet();

        final Thread initiatingJavaThread = Thread.currentThread();

        context.getSafepointManager().pauseAllThreadsAndExecute(currentNode, false, (thread, currentNode1) -> {
            synchronized (visited) {
                final Deque<Object> stack = new ArrayDeque<>();

                // Thread.current
                stack.add(thread);
                // Fiber.current
                stack.add(Layouts.THREAD.getFiberManager(thread).getCurrentFiber());

                if (Thread.currentThread() == initiatingJavaThread) {
                    visitContextRoots(context, stack);
                }

                Truffle.getRuntime().iterateFrames(frameInstance -> {
                    final Frame frame = frameInstance.getFrame(FrameAccess.READ_ONLY);
                    stack.addAll(getObjectsInFrame(frame));
                    return null;
                });

                while (!stack.isEmpty()) {
                    final Object object = stack.pop();

                    if (visited.add(object)) {
                        if (object instanceof DynamicObject) {
                            stack.addAll(ObjectGraph.getAdjacentObjects((DynamicObject) object));
                        }
                    }
                }
            }
        });

        return visited;
    }

    @TruffleBoundary
    public static Set<Object> stopAndGetRootObjects(Node currentNode, final RubyContext context) {
        final Set<Object> visited = newObjectSet();

        final Thread initiatingJavaThread = Thread.currentThread();

        context.getSafepointManager().pauseAllThreadsAndExecute(currentNode, false, (thread, currentNode1) -> {
            synchronized (visited) {
                visited.add(thread);

                if (Thread.currentThread() == initiatingJavaThread) {
                    visitContextRoots(context, visited);
                }
            }
        });

        return visited;
    }

    public static void visitContextRoots(RubyContext context, Collection<Object> roots) {
        // We do not want to expose the global object
        roots.addAll(context.getCoreLibrary().globalVariables.objectGraphValues());
        roots.addAll(context.getAtExitManager().getHandlers());
        context.getFinalizationService().collectRoots(roots);
    }

    public static boolean isSymbolOrDynamicObject(Object value) {
        return value instanceof DynamicObject || value instanceof RubySymbol;
    }

    public static Set<Object> getAdjacentObjects(DynamicObject object) {
        final Set<Object> reachable = newObjectSet();

        if (Layouts.BASIC_OBJECT.isBasicObject(object)) {
            reachable.add(Layouts.BASIC_OBJECT.getLogicalClass(object));
            reachable.add(Layouts.BASIC_OBJECT.getMetaClass(object));
        }

        for (Property property : object.getShape().getPropertyListInternal(false)) {
            final Object value = property.get(object, object.getShape());

            if (isSymbolOrDynamicObject(value)) {
                reachable.add(value);
            } else if (value instanceof Entry[]) {
                for (Entry bucket : (Entry[]) value) {
                    while (bucket != null) {
                        if (isSymbolOrDynamicObject(bucket.getKey())) {
                            reachable.add(bucket.getKey());
                        }

                        if (isSymbolOrDynamicObject(bucket.getValue())) {
                            reachable.add(bucket.getValue());
                        }

                        bucket = bucket.getNextInLookup();
                    }
                }
            } else if (value instanceof Object[]) {
                for (Object element : (Object[]) value) {
                    if (isSymbolOrDynamicObject(element)) {
                        reachable.add(element);
                    }
                }
            } else if (value instanceof Collection<?>) {
                for (Object element : ((Collection<?>) value)) {
                    if (isSymbolOrDynamicObject(element)) {
                        reachable.add(element);
                    }
                }
            } else if (value instanceof SizedQueue) {
                for (Object element : ((SizedQueue) value).getContents()) {
                    if (isSymbolOrDynamicObject(element)) {
                        reachable.add(element);
                    }
                }
            } else if (value instanceof UnsizedQueue) {
                for (Object element : ((UnsizedQueue) value).getContents()) {
                    if (isSymbolOrDynamicObject(element)) {
                        reachable.add(element);
                    }
                }
            } else if (value instanceof Frame) {
                reachable.addAll(getObjectsInFrame((Frame) value));
            } else if (value instanceof ObjectGraphNode) {
                ((ObjectGraphNode) value).getAdjacentObjects(reachable);
            }
        }

        return reachable;
    }

    public static Set<Object> getObjectsInFrame(Frame frame) {
        final Set<Object> objects = newObjectSet();

        final Frame lexicalParentFrame = RubyArguments.tryGetDeclarationFrame(frame);
        if (lexicalParentFrame != null) {
            objects.addAll(getObjectsInFrame(lexicalParentFrame));
        }

        final Object self = RubyArguments.tryGetSelf(frame);
        if (isSymbolOrDynamicObject(self)) {
            objects.add(self);
        }

        final DynamicObject block = RubyArguments.tryGetBlock(frame);
        if (block != null) {
            objects.add(block);
        }

        // Other frame arguments are either only internal or user arguments which appear in slots.

        for (FrameSlot slot : frame.getFrameDescriptor().getSlots()) {
            final Object slotValue = frame.getValue(slot);

            if (isSymbolOrDynamicObject(slotValue)) {
                objects.add(slotValue);
            }
        }

        return objects;
    }

}
