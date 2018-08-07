/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.graal.python.builtins.objects.bytes;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.nio.charset.CodingErrorAction;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PBytes.class)
public class BytesBuiltins extends PythonBuiltins {

    public static CodingErrorAction toCodingErrorAction(String errors, PBaseNode n) {
        switch (errors) {
            case "strict":
                return CodingErrorAction.REPORT;
            case "ignore":
                return CodingErrorAction.IGNORE;
            case "replace":
                return CodingErrorAction.REPLACE;
        }
        throw n.raise(PythonErrorType.LookupError, "unknown error handler name '%s'", errors);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BytesBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, takesVariableArguments = true, minNumOfArguments = 1, takesVariableKeywords = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public PNone init(Object self, Object args, Object kwargs) {
            // TODO: tfel: throw an error if we get additional arguments and the __new__
            // method was the same as object.__new__
            return PNone.NONE;
        }
    }

    @Builtin(name = __EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Child SequenceStorageNodes.EqNode eqNode;

        @Specialization
        public boolean eq(PBytes self, PByteArray other) {
            return getEqNode().execute(self.getSequenceStorage(), other.getSequenceStorage());
        }

        @Specialization
        public boolean eq(PBytes self, PBytes other) {
            return getEqNode().execute(self.getSequenceStorage(), other.getSequenceStorage());
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PBytes) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__eq__' requires a 'bytes' object but received a '%p'", self);
        }

        private SequenceStorageNodes.EqNode getEqNode() {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(SequenceStorageNodes.EqNode.create());
            }
            return eqNode;
        }
    }

    @Builtin(name = __NE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        public boolean eq(PBytes self, PByteArray other) {
            return !self.equals(other);
        }

        @Specialization
        public boolean eq(PBytes self, PBytes other) {
            return !self.equals(other);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PBytes) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__ne__' requires a 'bytes' object but received a '%p'", self);
        }
    }

    abstract static class CmpNode extends PythonBinaryBuiltinNode {
        int cmp(PBytes self, PBytes other) {
            byte[] a = self.getInternalByteArray();
            byte[] b = other.getInternalByteArray();
            for (int i = 0; i < Math.min(a.length, b.length); i++) {
                if (a[i] != b[i]) {
                    // CPython uses 'memcmp'; so do unsigned comparison
                    return a[i] & 0xFF - b[i] & 0xFF;
                }
            }
            return a.length - b.length;
        }
    }

    @Builtin(name = __LT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends CmpNode {
        @Specialization
        boolean doBytes(PBytes self, PBytes other) {
            return cmp(self, other) < 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends CmpNode {
        @Specialization
        boolean doBytes(PBytes self, PBytes other) {
            return cmp(self, other) <= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends CmpNode {
        @Specialization
        boolean doBytes(PBytes self, PBytes other) {
            return cmp(self, other) > 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends CmpNode {
        @Specialization
        boolean doBytes(PBytes self, PBytes other) {
            return cmp(self, other) >= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object add(PBytes self, PIBytesLike other) {
            return self.concat(factory(), other);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, "can't concat bytes to %p", other);
        }
    }

    @Builtin(name = __RADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class RAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object add(PBytes self, PIBytesLike other) {
            return self.concat(factory(), other);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, "can't concat bytes to %p", other);
        }
    }

    @Builtin(name = __MUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object mul(PBytes self, int times) {
            return self.__mul__(factory(), times);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object mul(Object self, Object other) {
            throw raise(TypeError, "can't multiply sequence by non-int of type '%p'", other);
        }
    }

    @Builtin(name = __RMUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __REPR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object repr(PBytes self) {
            return self.toString();
        }
    }

    // bytes.join(iterable)
    @Builtin(name = "join", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class JoinNode extends PythonBinaryBuiltinNode {
        @Specialization
        public PBytes join(PBytes bytes, Object iterable,
                        @Cached("create()") BytesNodes.BytesJoinNode bytesJoinNode) {
            return factory().createBytes(bytesJoinNode.execute(bytes.getInternalByteArray(), iterable));
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object arg) {
            throw raise(TypeError, "can only join an iterable");
        }
    }

    @Builtin(name = __LEN__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(PBytes self) {
            return self.len();
        }
    }

    @Builtin(name = __CONTAINS__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean contains(PBytes self, PBytes other) {
            return new String(self.getInternalByteArray()).contains(new String(other.getInternalByteArray()));
        }

        @Specialization
        @TruffleBoundary
        boolean contains(PBytes self, PByteArray other) {
            return new String(self.getInternalByteArray()).contains(new String(other.getInternalByteArray()));
        }

        @Specialization(guards = "!isBytes(other)")
        boolean contains(@SuppressWarnings("unused") PBytes self, Object other) {
            throw raise(TypeError, "a bytes-like object is required, not '%p'", other);
        }
    }

    @Builtin(name = __ITER__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        PSequenceIterator contains(PBytes self) {
            return factory().createSequenceIterator(self);
        }
    }

    @Builtin(name = "startswith", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    abstract static class StartsWithNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean startswith(PBytes self, String prefix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return new String(self.getInternalByteArray()).startsWith(prefix);
        }

        @Specialization
        boolean startswith(PBytes self, PIBytesLike prefix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return BytesUtils.startsWith(self, prefix);
        }

        @Specialization
        boolean startswith(PBytes self, PIBytesLike prefix, int start, @SuppressWarnings("unused") PNone end) {
            return BytesUtils.startsWith(self, prefix, start, -1);
        }

        @Specialization
        boolean startswith(PBytes self, PIBytesLike prefix, int start, int end) {
            return BytesUtils.startsWith(self, prefix, start, end);
        }
    }

    @Builtin(name = "endswith", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    abstract static class EndsWithNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean endswith(PBytes self, String prefix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return new String(self.getInternalByteArray()).endsWith(prefix);
        }

        @Specialization
        boolean endswith(PBytes self, PIBytesLike prefix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return BytesUtils.endsWith(self, prefix);
        }
    }

    @Builtin(name = "strip", minNumOfArguments = 1, maxNumOfArguments = 2, keywordArguments = {"bytes"})
    @GenerateNodeFactory
    abstract static class StripNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        PBytes strip(PBytes self, @SuppressWarnings("unused") PNone bytes) {
            return factory().createBytes(new String(self.getInternalByteArray()).trim().getBytes());
        }
    }

    // bytes.find(bytes[, start[, end]])
    @Builtin(name = "find", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    abstract static class FindNode extends PythonBuiltinNode {
        @Specialization
        int find(PBytes self, int sub, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, 0, self.len());
        }

        @Specialization
        int find(PBytes self, int sub, int start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, start, self.len());
        }

        @Specialization
        int find(PBytes self, int sub, int start, int ending) {
            return BytesUtils.find(self, sub, start, ending);
        }

        @Specialization
        int find(PBytes self, PIBytesLike sub, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, 0, self.len());
        }

        @Specialization
        int find(PBytes self, PIBytesLike sub, int start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, start, self.len());
        }

        @Specialization
        int find(PBytes self, PIBytesLike sub, int start, int ending) {
            return BytesUtils.find(self, sub, start, ending);
        }
    }

    @Builtin(name = __GETITEM__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isScalar(idx)")
        Object doScalar(PBytes self, Object idx,
                        @Cached("create()") SequenceStorageNodes.GetItemNode getSequenceItemNode) {
            return getSequenceItemNode.execute(self.getSequenceStorage(), idx);
        }

        @Specialization
        Object doSlice(PBytes self, PSlice slice,
                        @Cached("create()") SequenceStorageNodes.GetItemNode getSequenceItemNode) {
            return factory().createBytes(self.getPythonClass(), (ByteSequenceStorage) getSequenceItemNode.execute(self.getSequenceStorage(), slice));
        }

        protected boolean isScalar(Object obj) {
            return PGuards.isInteger(obj) || PGuards.isPInt(obj);
        }
    }

    @Builtin(name = __SETITEM__, fixedNumOfArguments = 3)
    @GenerateNodeFactory
    abstract static class SetitemNode extends PythonTernaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object getitem(PBytes self, Object idx, Object value) {
            throw raise(TypeError, "'bytes' object does not support item assignment");
        }
    }
}
