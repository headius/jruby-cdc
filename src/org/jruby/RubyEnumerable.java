/***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby;

import java.util.Comparator;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.jruby.anno.JRubyMethod;
import org.jruby.anno.JRubyModule;

import org.jruby.exceptions.JumpException;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.CallBlock;
import org.jruby.runtime.BlockCallback;
import org.jruby.runtime.MethodIndex;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.TypeConverter;

/**
 * The implementation of Ruby's Enumerable module.
 */

@JRubyModule(name="Enumerable")
public class RubyEnumerable {

    public static RubyModule createEnumerableModule(Ruby runtime) {
        RubyModule enumModule = runtime.defineModule("Enumerable");
        runtime.setEnumerable(enumModule);
        
        enumModule.defineAnnotatedMethods(RubyEnumerable.class);

        return enumModule;
    }

    public static IRubyObject callEach(Ruby runtime, ThreadContext context, IRubyObject self,
            BlockCallback callback) {
        return RuntimeHelpers.invoke(context, self, "each", CallBlock.newCallClosure(self, runtime.getEnumerable(), 
                Arity.noArguments(), callback, context));
    }

    @JRubyMethod(name = "first")
    public static IRubyObject first_0(ThreadContext context, IRubyObject self) {
        Ruby runtime = self.getRuntime();
        final ThreadContext localContext = context;
        
        final IRubyObject[] holder = new IRubyObject[]{runtime.getNil()};

        try {
            callEach(runtime, context, self, new BlockCallback() {
                    public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                        if (localContext != ctx) {
                            throw ctx.getRuntime().newThreadError("Enumerable#first cannot be parallelized");
                        }
                        holder[0] = largs[0];
                        throw JumpException.SPECIAL_JUMP;
                    }
                });
        } catch (JumpException.SpecialJump sj) {}

        return holder[0];
    }

    @JRubyMethod(name = "first")
    public static IRubyObject first_1(ThreadContext context, IRubyObject self, final IRubyObject num) {
        final Ruby runtime = self.getRuntime();
        final RubyArray result = runtime.newArray();
        final ThreadContext localContext = context;

        if(RubyNumeric.fix2int(num) < 0) {
            throw runtime.newArgumentError("negative index");
        }

        try {
            callEach(runtime, context, self, new BlockCallback() {
                    private int iter = RubyNumeric.fix2int(num);
                    public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                        if (localContext != ctx) {
                            throw runtime.newThreadError("Enumerable#first cannot be parallelized");
                        }
                        if (iter-- == 0) {
                            throw JumpException.SPECIAL_JUMP;
                        }
                        result.append(largs[0]);
                        return runtime.getNil();
                    }
                });
        } catch (JumpException.SpecialJump sj) {}

        return result;
    }

    @JRubyMethod(name = {"to_a", "entries"})
    public static IRubyObject to_a(ThreadContext context, IRubyObject self) {
        Ruby runtime = self.getRuntime();
        RubyArray result = runtime.newArray();

        callEach(runtime, context, self, new AppendBlockCallback(runtime, result));

        return result;
    }

    @JRubyMethod(name = "sort", frame = true)
    public static IRubyObject sort(ThreadContext context, IRubyObject self, final Block block) {
        Ruby runtime = self.getRuntime();
        RubyArray result = runtime.newArray();

        callEach(runtime, context, self, new AppendBlockCallback(runtime, result));
        result.sort_bang(block);
        
        return result;
    }

    @JRubyMethod(name = "sort_by", frame = true)
    public static IRubyObject sort_by(ThreadContext context, IRubyObject self, final Block block) {
        final Ruby runtime = self.getRuntime();
        final ThreadContext localContext = context; // MUST NOT be used across threads

        if (self instanceof RubyArray) {
            RubyArray selfArray = (RubyArray) self;
            final IRubyObject[][] valuesAndCriteria = new IRubyObject[selfArray.size()][2];

            callEach(runtime, context, self, new BlockCallback() {
                AtomicInteger i = new AtomicInteger(0);

                public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                    IRubyObject[] myVandC = valuesAndCriteria[i.getAndIncrement()];
                    myVandC[0] = largs[0];
                    myVandC[1] = block.yield(ctx, largs[0]);
                    return runtime.getNil();
                }
            });
            
            Arrays.sort(valuesAndCriteria, new Comparator<IRubyObject[]>() {
                public int compare(IRubyObject[] o1, IRubyObject[] o2) {
                    return RubyFixnum.fix2int(o1[1].callMethod(localContext, MethodIndex.OP_SPACESHIP, "<=>", o2[1]));
                }
            });
            
            IRubyObject dstArray[] = new IRubyObject[selfArray.size()];
            for (int i = 0; i < dstArray.length; i++) {
                dstArray[i] = valuesAndCriteria[i][0];
            }

            return runtime.newArrayNoCopy(dstArray);
        } else {
            final RubyArray result = runtime.newArray();
            callEach(runtime, context, self, new AppendBlockCallback(runtime, result));
            
            final IRubyObject[][] valuesAndCriteria = new IRubyObject[result.size()][2];
            for (int i = 0; i < valuesAndCriteria.length; i++) {
                IRubyObject val = result.eltInternal(i);
                valuesAndCriteria[i][0] = val;
                valuesAndCriteria[i][1] = block.yield(context, val);
            }
            
            Arrays.sort(valuesAndCriteria, new Comparator<IRubyObject[]>() {
                public int compare(IRubyObject[] o1, IRubyObject[] o2) {
                    return RubyFixnum.fix2int(o1[1].callMethod(localContext, MethodIndex.OP_SPACESHIP, "<=>", o2[1]));
                }
            });
            
            for (int i = 0; i < valuesAndCriteria.length; i++) {
                result.eltInternalSet(i, valuesAndCriteria[i][0]);
            }

            return result;
        }
    }

    @JRubyMethod(name = "grep", required = 1, frame = true)
    public static IRubyObject grep(ThreadContext context, IRubyObject self, final IRubyObject pattern, final Block block) {
        final Ruby runtime = self.getRuntime();
        final RubyArray result = runtime.newArray();

        if (block.isGiven()) {
            callEach(runtime, context, self, new BlockCallback() {
                public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                    ctx.setRubyFrameDelta(ctx.getRubyFrameDelta()+2);
                    if (pattern.callMethod(ctx, MethodIndex.OP_EQQ, "===", largs[0]).isTrue()) {
                        IRubyObject value = block.yield(ctx, largs[0]);
                        synchronized (result) {
                            result.append(value);
                        }
                    }
                    ctx.setRubyFrameDelta(ctx.getRubyFrameDelta()-2);
                    return runtime.getNil();
                }
            });
        } else {
            callEach(runtime, context, self, new BlockCallback() {
                public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                    if (pattern.callMethod(ctx, MethodIndex.OP_EQQ, "===", largs[0]).isTrue()) {
                        synchronized (result) {
                            result.append(largs[0]);
                        }
                    }
                    return runtime.getNil();
                }
            });
        }
        
        return result;
    }

    @JRubyMethod(name = {"detect", "find"}, optional = 1, frame = true)
    public static IRubyObject detect(ThreadContext context, IRubyObject self, IRubyObject[] args, final Block block) {
        final Ruby runtime = self.getRuntime();
        final IRubyObject result[] = new IRubyObject[] { null };
        final ThreadContext localContext = context;
        IRubyObject ifnone = null;

        if (args.length == 1) ifnone = args[0];

        try {
            callEach(runtime, context, self, new BlockCallback() {
                public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                    if (localContext != ctx) {
                        throw runtime.newThreadError("Enumerable#detect/find cannot be parallelized");
                    }
                    if (block.yield(ctx, largs[0]).isTrue()) {
                        result[0] = largs[0];
                        throw JumpException.SPECIAL_JUMP;
                    }
                    return runtime.getNil();
                }
            });
        } catch (JumpException.SpecialJump sj) {
            return result[0];
        }

        return ifnone != null ? ifnone.callMethod(context, "call") : runtime.getNil();
    }

    @JRubyMethod(name = {"select", "find_all"}, frame = true)
    public static IRubyObject select(ThreadContext context, IRubyObject self, final Block block) {
        final Ruby runtime = self.getRuntime();
        final RubyArray result = runtime.newArray();

        callEach(runtime, context, self, new BlockCallback() {
            public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                if (block.yield(ctx, largs[0]).isTrue()) {
                    synchronized (result) {
                        result.append(largs[0]);
                    }
                }
                return runtime.getNil();
            }
        });

        return result;
    }

    @JRubyMethod(name = "reject", frame = true)
    public static IRubyObject reject(ThreadContext context, IRubyObject self, final Block block) {
        final Ruby runtime = self.getRuntime();
        final RubyArray result = runtime.newArray();

        callEach(runtime, context, self, new BlockCallback() {
            public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                if (!block.yield(ctx, largs[0]).isTrue()) {
                    synchronized (result) {
                        result.append(largs[0]);
                    }
                }
                return runtime.getNil();
            }
        });

        return result;
    }

    @JRubyMethod(name = {"collect", "map"}, frame = true)
    public static IRubyObject collect(ThreadContext context, IRubyObject self, final Block block) {
        final Ruby runtime = self.getRuntime();
        final RubyArray result = runtime.newArray();

        if (block.isGiven()) {
            callEach(runtime, context, self, new BlockCallback() {
                public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                    IRubyObject value = block.yield(ctx, largs[0]);
                    synchronized (result) {
                        result.append(value);
                    }
                    return runtime.getNil();
                }
            });
        } else {
            callEach(runtime, context, self, new AppendBlockCallback(runtime, result));
        }
        return result;
    }

    @JRubyMethod(name = "inject", optional = 1, frame = true)
    public static IRubyObject inject(ThreadContext context, IRubyObject self, IRubyObject[] args, final Block block) {
        final Ruby runtime = self.getRuntime();
        final IRubyObject result[] = new IRubyObject[] { null };
        final ThreadContext localContext = context;

        if (args.length == 1) result[0] = args[0];

        callEach(runtime, context, self, new BlockCallback() {
            public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                if (localContext != ctx) {
                    throw runtime.newThreadError("Enumerable#inject cannot be parallelized");
                }
                result[0] = result[0] == null ? 
                        largs[0] : block.yield(ctx, runtime.newArray(result[0], largs[0]), null, null, true);

                return runtime.getNil();
            }
        });

        return result[0] == null ? runtime.getNil() : result[0];
    }

    @JRubyMethod(name = "partition", frame = true)
    public static IRubyObject partition(ThreadContext context, IRubyObject self, final Block block) {
        final Ruby runtime = self.getRuntime();
        final RubyArray arr_true = runtime.newArray();
        final RubyArray arr_false = runtime.newArray();

        callEach(runtime, context, self, new BlockCallback() {
            public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                if (block.yield(ctx, largs[0]).isTrue()) {
                    synchronized (arr_true) {
                        arr_true.append(largs[0]);
                    }
                } else {
                    synchronized (arr_false) {
                        arr_false.append(largs[0]);
                    }
                }

                return runtime.getNil();
            }
        });

        return runtime.newArray(arr_true, arr_false);
    }

    private static class EachWithIndex implements BlockCallback {
        private int index = 0;
        private final Block block;
        private final Ruby runtime;

        public EachWithIndex(ThreadContext ctx, Block block) {
            this.block = block;
            this.runtime = ctx.getRuntime();
        }

        public IRubyObject call(ThreadContext context, IRubyObject[] iargs, Block block) {
            this.block.call(context, new IRubyObject[] { runtime.newArray(iargs[0], runtime.newFixnum(index++)) });
            return runtime.getNil();            
        }
    }

    @JRubyMethod(name = "each_with_index", frame = true)
    public static IRubyObject each_with_index(ThreadContext context, IRubyObject self, Block block) {
        RuntimeHelpers.invoke(context, self, "each", CallBlock.newCallClosure(self, self.getRuntime().getEnumerable(), 
                Arity.noArguments(), new EachWithIndex(context, block), context));
        
        return self;
    }

    @JRubyMethod(name = {"include?", "member?"}, required = 1, frame = true)
    public static IRubyObject include_p(ThreadContext context, IRubyObject self, final IRubyObject arg) {
        final Ruby runtime = context.getRuntime();
        final ThreadContext localContext = context;

        try {
            callEach(runtime, context, self, new BlockCallback() {
                public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                    if (localContext != ctx) {
                        throw runtime.newThreadError("Enumerable#include?/member? cannot be parallelized");
                    }
                    if (RubyObject.equalInternal(ctx, largs[0], arg)) {
                        throw JumpException.SPECIAL_JUMP;
                    }
                    return runtime.getNil();
                }
            });
        } catch (JumpException.SpecialJump sj) {
            return runtime.getTrue();
        }
        
        return runtime.getFalse();
    }

    @JRubyMethod(name = "max", frame = true)
    public static IRubyObject max(ThreadContext context, IRubyObject self, final Block block) {
        final Ruby runtime = self.getRuntime();
        final IRubyObject result[] = new IRubyObject[] { null };
        final ThreadContext localContext = context;

        if (block.isGiven()) {
            callEach(runtime, context, self, new BlockCallback() {
                public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                    if (localContext != ctx) {
                        throw runtime.newThreadError("Enumerable#max{} cannot be parallelized");
                    }
                    if (result[0] == null || RubyComparable.cmpint(ctx, block.yield(ctx, 
                            runtime.newArray(largs[0], result[0])), largs[0], result[0]) > 0) {
                        result[0] = largs[0];
                    }
                    return runtime.getNil();
                }
            });
        } else {
            callEach(runtime, context, self, new BlockCallback() {
                public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                    synchronized (result) {
                        if (result[0] == null || RubyComparable.cmpint(ctx, largs[0].callMethod(ctx,
                                MethodIndex.OP_SPACESHIP, "<=>", result[0]), largs[0], result[0]) > 0) {
                            result[0] = largs[0];
                        }
                    }
                    return runtime.getNil();
                }
            });
        }
        
        return result[0] == null ? runtime.getNil() : result[0];
    }

    @JRubyMethod(name = "min", frame = true)
    public static IRubyObject min(ThreadContext context, IRubyObject self, final Block block) {
        final Ruby runtime = self.getRuntime();
        final IRubyObject result[] = new IRubyObject[] { null };
        final ThreadContext localContext = context;

        if (block.isGiven()) {
            callEach(runtime, context, self, new BlockCallback() {
                public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                    if (localContext != ctx) {
                        throw runtime.newThreadError("Enumerable#min{} cannot be parallelized");
                    }
                    if (result[0] == null || RubyComparable.cmpint(ctx, block.yield(ctx, 
                            runtime.newArray(largs[0], result[0])), largs[0], result[0]) < 0) {
                        result[0] = largs[0];
                    }
                    return runtime.getNil();
                }
            });
        } else {
            callEach(runtime, context, self, new BlockCallback() {
                public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                    synchronized (result) {
                        if (result[0] == null || RubyComparable.cmpint(ctx, largs[0].callMethod(ctx,
                                MethodIndex.OP_SPACESHIP, "<=>", result[0]), largs[0], result[0]) < 0) {
                            result[0] = largs[0];
                        }
                    }
                    return runtime.getNil();
                }
            });
        }
        
        return result[0] == null ? runtime.getNil() : result[0];
    }

    @JRubyMethod(name = "all?", frame = true)
    public static IRubyObject all_p(ThreadContext context, IRubyObject self, final Block block) {
        final Ruby runtime = self.getRuntime();
        final ThreadContext localContext = context;

        try {
            if (block.isGiven()) {
                callEach(runtime, context, self, new BlockCallback() {
                    public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                        if (localContext != ctx) {
                            throw runtime.newThreadError("Enumerable#all? cannot be parallelized");
                        }
                        if (!block.yield(ctx, largs[0]).isTrue()) {
                            throw JumpException.SPECIAL_JUMP;
                        }
                        return runtime.getNil();
                    }
                });
            } else {
                callEach(runtime, context, self, new BlockCallback() {
                    public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                        if (localContext != ctx) {
                            throw runtime.newThreadError("Enumerable#all? cannot be parallelized");
                        }
                        if (!largs[0].isTrue()) {
                            throw JumpException.SPECIAL_JUMP;
                        }
                        return runtime.getNil();
                    }
                });
            }
        } catch (JumpException.SpecialJump sj) {
            return runtime.getFalse();
        }

        return runtime.getTrue();
    }

    @JRubyMethod(name = "any?", frame = true)
    public static IRubyObject any_p(ThreadContext context, IRubyObject self, final Block block) {
        final Ruby runtime = self.getRuntime();
        final ThreadContext localContext = context;

        try {
            if (block.isGiven()) {
                callEach(runtime, context, self, new BlockCallback() {
                    public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                        if (localContext != ctx) {
                            throw runtime.newThreadError("Enumerable#any? cannot be parallelized");
                        }
                        if (block.yield(ctx, largs[0]).isTrue()) {
                            throw JumpException.SPECIAL_JUMP;
                        }
                        return runtime.getNil();
                    }
                });
            } else {
                callEach(runtime, context, self, new BlockCallback() {
                    public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                        if (localContext != ctx) {
                            throw runtime.newThreadError("Enumerable#any? cannot be parallelized");
                        }
                        if (largs[0].isTrue()) {
                            throw JumpException.SPECIAL_JUMP;
                        }
                        return runtime.getNil();
                    }
                });
            }
        } catch (JumpException.SpecialJump sj) {
            return runtime.getTrue();
        }

        return runtime.getFalse();
    }

    @JRubyMethod(name = "zip", rest = true, frame = true)
    public static IRubyObject zip(ThreadContext context, IRubyObject self, final IRubyObject[] args, final Block block) {
        final Ruby runtime = self.getRuntime();

        for (int i = 0; i < args.length; i++) {
            args[i] = TypeConverter.convertToType(args[i], runtime.getArray(), MethodIndex.TO_A, "to_a");
        }
        
        final int aLen = args.length + 1;

        if (block.isGiven()) {
            callEach(runtime, context, self, new BlockCallback() {
                AtomicInteger ix = new AtomicInteger(0);

                public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                    RubyArray array = runtime.newArray(aLen);
                    int myIx = ix.getAndIncrement();
                    array.append(largs[0]);
                    for (int i = 0, j = args.length; i < j; i++) {
                        array.append(((RubyArray) args[i]).entry(myIx));
                    }
                    block.yield(ctx, array);
                    return runtime.getNil();
                }
            });
            return runtime.getNil();
        } else {
            final RubyArray zip = runtime.newArray();
            callEach(runtime, context, self, new BlockCallback() {
                AtomicInteger ix = new AtomicInteger(0);

                public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                    RubyArray array = runtime.newArray(aLen);
                    array.append(largs[0]);
                    int myIx = ix.getAndIncrement();
                    for (int i = 0, j = args.length; i < j; i++) {
                        array.append(((RubyArray) args[i]).entry(myIx));
                    }
                    synchronized (zip) {
                        zip.append(array);
                    }
                    return runtime.getNil();
                }
            });
            return zip;
        }
    }

    @JRubyMethod(name = "group_by", frame = true)
    public static IRubyObject group_by(ThreadContext context, IRubyObject self, final Block block) {
        final Ruby runtime = self.getRuntime();
        final RubyHash result = new RubyHash(runtime);

        callEach(runtime, context, self, new BlockCallback() {
            public IRubyObject call(ThreadContext ctx, IRubyObject[] largs, Block blk) {
                IRubyObject key = block.yield(ctx, largs[0]);
                synchronized (result) {
                    IRubyObject curr = result.fastARef(key);

                    if (curr == null) {
                        curr = runtime.newArray();
                        result.fastASet(key, curr);
                    }
                    curr.callMethod(ctx, MethodIndex.OP_LSHIFT, "<<", largs[0]);
                }
                return runtime.getNil();
            }
        });

        return result;
    }
    
    public static final class AppendBlockCallback implements BlockCallback {
        private Ruby runtime;
        private RubyArray result;

        public AppendBlockCallback(Ruby runtime, RubyArray result) {
            this.runtime = runtime;
            this.result = result;
        }
        
        public IRubyObject call(ThreadContext context, IRubyObject[] largs, Block blk) {
            result.append(largs[0]);
            
            return runtime.getNil();
        }
    }
}
