package org.jruby.javasupport.util;

import org.jruby.MetaClass;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
import org.jruby.RubyException;
import org.jruby.RubyFixnum;
import org.jruby.RubyHash;
import org.jruby.RubyInstanceConfig;
import org.jruby.RubyKernel;
import org.jruby.RubyLocalJumpError;
import org.jruby.RubyMatchData;
import org.jruby.RubyModule;
import org.jruby.RubyProc;
import org.jruby.RubyRegexp;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.ast.util.ArgsUtil;
import org.jruby.common.IRubyWarnings.ID;
import org.jruby.evaluator.ASTInterpreter;
import org.jruby.exceptions.JumpException;
import org.jruby.exceptions.RaiseException;
import org.jruby.internal.runtime.methods.CallConfiguration;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.internal.runtime.methods.WrapperMethod;
import org.jruby.javasupport.JavaClass;
import org.jruby.javasupport.JavaUtil;
import org.jruby.parser.BlockStaticScope;
import org.jruby.parser.LocalStaticScope;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.BlockBody;
import org.jruby.runtime.CallSite;
import org.jruby.runtime.CallType;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.CompiledBlock;
import org.jruby.runtime.CompiledBlockCallback;
import org.jruby.runtime.CompiledBlockLight;
import org.jruby.runtime.CompiledSharedScopeBlock;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.MethodFactory;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.TypeConverter;

/**
 * Helper methods which are called by the compiler.  Note: These will show no consumers, but
 * generated code does call these so don't remove them thinking they are dead code. 
 *
 */
public class RuntimeHelpers {
    public static IRubyObject doAttrAsgn(IRubyObject value, IRubyObject receiver, ThreadContext context, IRubyObject caller, CallSite callSite) {
        callSite.call(context, caller, receiver, value);
        return value;
    }
    public static IRubyObject doAttrAsgn(IRubyObject value, IRubyObject receiver, IRubyObject arg0, ThreadContext context, IRubyObject caller, CallSite callSite) {
        callSite.call(context, caller, receiver, arg0, value);
        return value;
    }
    public static IRubyObject doAttrAsgn(IRubyObject value, IRubyObject receiver, IRubyObject arg0, IRubyObject arg1, ThreadContext context, IRubyObject caller, CallSite callSite) {
        callSite.call(context, caller, receiver, arg0, arg1, value);
        return value;
    }
    public static IRubyObject doAttrAsgn(IRubyObject value, IRubyObject receiver, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, ThreadContext context, IRubyObject caller, CallSite callSite) {
        callSite.call(context, caller, receiver, arg0, arg1, arg2, value);
        return value;
    }
    public static IRubyObject doAttrAsgn(IRubyObject value, IRubyObject receiver, IRubyObject[] args, ThreadContext context, IRubyObject caller, CallSite callSite) {
        callSite.call(context, caller, receiver, appendToObjectArray(args, value));
        return value;
    }
    public static IRubyObject doAttrAsgn(IRubyObject receiver, CallSite callSite, IRubyObject value, ThreadContext context, IRubyObject caller) {
        callSite.call(context, caller, receiver, value);
        return value;
    }
    public static IRubyObject doAttrAsgn(IRubyObject receiver, CallSite callSite, IRubyObject arg0, IRubyObject value, ThreadContext context, IRubyObject caller) {
        callSite.call(context, caller, receiver, arg0, value);
        return value;
    }
    public static IRubyObject doAttrAsgn(IRubyObject receiver, CallSite callSite, IRubyObject arg0, IRubyObject arg1, IRubyObject value, ThreadContext context, IRubyObject caller) {
        callSite.call(context, caller, receiver, arg0, arg1, value);
        return value;
    }
    public static IRubyObject doAttrAsgn(IRubyObject receiver, CallSite callSite, IRubyObject[] args, ThreadContext context, IRubyObject caller) {
        callSite.call(context, caller, receiver, args);
        return args[args.length - 1];
    }

    public static IRubyObject invokeEqqForCaseWhen(IRubyObject receiver, IRubyObject arg, CallSite callSite, ThreadContext context, IRubyObject caller) {
        return callSite.call(context, caller, receiver, arg);
    }
    
    public static CompiledBlockCallback createBlockCallback(Ruby runtime, Object scriptObject, String closureMethod) {
        Class scriptClass = scriptObject.getClass();
        ClassLoader scriptClassLoader = scriptClass.getClassLoader();
        CallbackFactory factory = CallbackFactory.createFactory(runtime, scriptClass, scriptClassLoader);
        
        return factory.getBlockCallback(closureMethod, scriptObject);
    }
    
    public static BlockBody createCompiledBlockBody(ThreadContext context, Object scriptObject, String closureMethod, int arity, 
            String[] staticScopeNames, boolean hasMultipleArgsHead, int argsNodeType, boolean light) {
        StaticScope staticScope = 
            new BlockStaticScope(context.getCurrentScope().getStaticScope(), staticScopeNames);
        staticScope.determineModule();
        
        if (light) {
            return CompiledBlockLight.newCompiledBlockLight(
                    Arity.createArity(arity), staticScope,
                    createBlockCallback(context.getRuntime(), scriptObject, closureMethod),
                    hasMultipleArgsHead, argsNodeType);
        } else {
            return CompiledBlock.newCompiledBlock(
                    Arity.createArity(arity), staticScope,
                    createBlockCallback(context.getRuntime(), scriptObject, closureMethod),
                    hasMultipleArgsHead, argsNodeType);
        }
    }
    
    public static Block createBlock(ThreadContext context, IRubyObject self, BlockBody body) {
        return CompiledBlock.newCompiledClosure(
                context,
                self,
                body);
    }
    
    public static IRubyObject runBeginBlock(ThreadContext context, IRubyObject self, String[] staticScopeNames, CompiledBlockCallback callback) {
        StaticScope staticScope = 
            new BlockStaticScope(context.getCurrentScope().getStaticScope(), staticScopeNames);
        staticScope.determineModule();
        
        context.preScopedBody(DynamicScope.newDynamicScope(staticScope, context.getCurrentScope()));
        
        Block block = CompiledBlock.newCompiledClosure(context, self, Arity.createArity(0), staticScope, callback, false, BlockBody.ZERO_ARGS);
        
        try {
            block.yield(context, null);
        } finally {
            context.postScopedBody();
        }
        
        return context.getRuntime().getNil();
    }
    
    public static Block createSharedScopeBlock(ThreadContext context, IRubyObject self, int arity, 
            CompiledBlockCallback callback, boolean hasMultipleArgsHead, int argsNodeType) {
        
        return CompiledSharedScopeBlock.newCompiledSharedScopeClosure(context, self, Arity.createArity(arity), 
                context.getCurrentScope(), callback, hasMultipleArgsHead, argsNodeType);
    }
    
    public static IRubyObject def(ThreadContext context, IRubyObject self, Object scriptObject, String name, String javaName, String[] scopeNames,
            int arity, int required, int optional, int rest, CallConfiguration callConfig) {
        Class compiledClass = scriptObject.getClass();
        Ruby runtime = context.getRuntime();
        
        RubyModule containingClass = context.getRubyClass();
        Visibility visibility = context.getCurrentVisibility();
        
        performNormalMethodChecks(containingClass, runtime, name);
        
        StaticScope scope = creatScopeForClass(context, scopeNames, required, optional, rest);
        
        MethodFactory factory = MethodFactory.createFactory(compiledClass.getClassLoader());
        DynamicMethod method = constructNormalMethod(name, visibility, factory, containingClass, javaName, arity, scope, scriptObject, callConfig);
        
        addInstanceMethod(containingClass, name, method, visibility,context, runtime);
        
        return runtime.getNil();
    }
    
    public static IRubyObject defs(ThreadContext context, IRubyObject self, IRubyObject receiver, Object scriptObject, String name, String javaName, String[] scopeNames,
            int arity, int required, int optional, int rest, CallConfiguration callConfig) {
        Class compiledClass = scriptObject.getClass();
        Ruby runtime = context.getRuntime();

        RubyClass rubyClass = performSingletonMethodChecks(runtime, receiver, name);
        
        StaticScope scope = creatScopeForClass(context, scopeNames, required, optional, rest);
        
        MethodFactory factory = MethodFactory.createFactory(compiledClass.getClassLoader());
        DynamicMethod method = constructSingletonMethod(factory, rubyClass, javaName, arity, scope,scriptObject, callConfig);
        
        rubyClass.addMethod(name, method);
        
        callSingletonMethodHook(receiver,context, runtime.fastNewSymbol(name));
        
        return runtime.getNil();
    }
    
    public static RubyClass getSingletonClass(Ruby runtime, IRubyObject receiver) {
        if (receiver instanceof RubyFixnum || receiver instanceof RubySymbol) {
            throw runtime.newTypeError("no virtual class for " + receiver.getMetaClass().getBaseName());
        } else {
            if (runtime.getSafeLevel() >= 4 && !receiver.isTaint()) {
                throw runtime.newSecurityError("Insecure: can't extend object.");
            }

            return receiver.getSingletonClass();
        }
    }

    // TODO: Only used by interface implementation; eliminate it
    public static IRubyObject invokeMethodMissing(IRubyObject receiver, String name, IRubyObject[] args) {
        ThreadContext context = receiver.getRuntime().getCurrentContext();

        // store call information so method_missing impl can use it
        context.setLastCallStatusAndVisibility(CallType.FUNCTIONAL, Visibility.PUBLIC);

        if (name == "method_missing") {
            return RubyKernel.method_missing(context, receiver, args, Block.NULL_BLOCK);
        }

        IRubyObject[] newArgs = prepareMethodMissingArgs(args, context, name);

        return invoke(context, receiver, "method_missing", newArgs, Block.NULL_BLOCK);
    }

    public static IRubyObject callMethodMissing(ThreadContext context, IRubyObject receiver, DynamicMethod method, String name, 
                                                IRubyObject[] args, CallType callType, Block block) {
        context.setLastCallStatusAndVisibility(callType, method.getVisibility());
        return callMethodMissingInternal(context, receiver, name, args, block);
    }
    public static IRubyObject callMethodMissing(ThreadContext context, IRubyObject receiver, DynamicMethod method, String name, 
                                                IRubyObject arg, CallType callType, Block block) {
        context.setLastCallStatusAndVisibility(callType, method.getVisibility());
        return callMethodMissingInternal(context, receiver, name, arg, block);
    }
    public static IRubyObject callMethodMissing(ThreadContext context, IRubyObject receiver, DynamicMethod method, String name, 
                                                IRubyObject arg0, IRubyObject arg1, CallType callType, Block block) {
        context.setLastCallStatusAndVisibility(callType, method.getVisibility());
        return callMethodMissingInternal(context, receiver, name, arg0, arg1, block);
    }
    public static IRubyObject callMethodMissing(ThreadContext context, IRubyObject receiver, DynamicMethod method, String name, 
                                                IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, CallType callType, Block block) {
        context.setLastCallStatusAndVisibility(callType, method.getVisibility());
        return callMethodMissingInternal(context, receiver, name, arg0, arg1, arg2, block);
    }
    public static IRubyObject callMethodMissing(ThreadContext context, IRubyObject receiver, DynamicMethod method, String name, 
                                                CallType callType, Block block) {
        context.setLastCallStatusAndVisibility(callType, method.getVisibility());
        return callMethodMissingInternal(context, receiver, name, block);
    }
    
    private static IRubyObject callMethodMissingInternal(ThreadContext context, IRubyObject receiver, String name, 
                                                IRubyObject[] args, Block block) {
        if (name.equals("method_missing")) return RubyKernel.method_missing(context, receiver, args, block);
        IRubyObject[] newArgs = prepareMethodMissingArgs(args, context, name);
        return invoke(context, receiver, "method_missing", newArgs, block);
    }
    private static IRubyObject callMethodMissingInternal(ThreadContext context, IRubyObject receiver, String name, 
                                                Block block) {
        if (name.equals("method_missing")) return RubyKernel.method_missing(context, receiver, IRubyObject.NULL_ARRAY, block);
        return invoke(context, receiver, "method_missing", context.getRuntime().newSymbol(name), block);
    }
    private static IRubyObject callMethodMissingInternal(ThreadContext context, IRubyObject receiver, String name, 
                                                IRubyObject arg0, Block block) {
        if (name.equals("method_missing")) return RubyKernel.method_missing(context, receiver, constructObjectArray(arg0), block);
        return invoke(context, receiver, "method_missing", context.getRuntime().newSymbol(name), arg0, block);
    }
    private static IRubyObject callMethodMissingInternal(ThreadContext context, IRubyObject receiver, String name, 
                                                IRubyObject arg0, IRubyObject arg1, Block block) {
        if (name.equals("method_missing")) return RubyKernel.method_missing(context, receiver, constructObjectArray(arg0,arg1), block);
        return invoke(context, receiver, "method_missing", context.getRuntime().newSymbol(name), arg0, arg1, block);
    }
    private static IRubyObject callMethodMissingInternal(ThreadContext context, IRubyObject receiver, String name, 
                                                IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, Block block) {
        if (name.equals("method_missing")) return RubyKernel.method_missing(context, receiver, constructObjectArray(arg0,arg1,arg2), block);
        return invoke(context, receiver, "method_missing", constructObjectArray(context.getRuntime().newSymbol(name), arg0, arg1, arg2), block);
    }

    private static IRubyObject[] prepareMethodMissingArgs(IRubyObject[] args, ThreadContext context, String name) {
        IRubyObject[] newArgs = new IRubyObject[args.length + 1];
        System.arraycopy(args, 0, newArgs, 1, args.length);
        newArgs[0] = context.getRuntime().newSymbol(name);

        return newArgs;
    }
    
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name, Block block) {
        return self.getMetaClass().finvoke(context, self, name, block);
    }
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name, IRubyObject arg0, Block block) {
        return self.getMetaClass().finvoke(context, self, name, arg0, block);
    }
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name, IRubyObject arg0, IRubyObject arg1, Block block) {
        return self.getMetaClass().finvoke(context, self, name, arg0, arg1, block);
    }
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, Block block) {
        return self.getMetaClass().finvoke(context, self, name, arg0, arg1, arg2, block);
    }
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name, IRubyObject[] args, Block block) {
        return self.getMetaClass().finvoke(context, self, name, args, block);
    }
    
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name) {
        return self.getMetaClass().finvoke(context, self, name);
    }
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name, IRubyObject arg0) {
        return self.getMetaClass().finvoke(context, self, name, arg0);
    }
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name, IRubyObject arg0, IRubyObject arg1) {
        return self.getMetaClass().finvoke(context, self, name, arg0, arg1);
    }
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2) {
        return self.getMetaClass().finvoke(context, self, name, arg0, arg1, arg2);
    }
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name, IRubyObject[] args) {
        return self.getMetaClass().finvoke(context, self, name, args);
    }
    
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name, CallType callType) {
        return RuntimeHelpers.invoke(context, self, name, IRubyObject.NULL_ARRAY, callType, Block.NULL_BLOCK);
    }
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name, IRubyObject[] args, CallType callType, Block block) {
        return self.getMetaClass().invoke(context, self, name, args, callType, block);
    }
    
    public static IRubyObject invoke(ThreadContext context, IRubyObject self, String name, IRubyObject arg, CallType callType, Block block) {
        return self.getMetaClass().invoke(context, self, name, arg, callType, block);
    }
    
    public static IRubyObject invokeAs(ThreadContext context, RubyClass asClass, IRubyObject self, String name, IRubyObject[] args, Block block) {
        return asClass.finvoke(context, self, name, args, block);
    }
    
    public static IRubyObject invokeAs(ThreadContext context, RubyClass asClass, IRubyObject self, String name, Block block) {
        return asClass.finvoke(context, self, name, block);
    }
    
    public static IRubyObject invokeAs(ThreadContext context, RubyClass asClass, IRubyObject self, String name, IRubyObject arg0, Block block) {
        return asClass.finvoke(context, self, name, arg0, block);
    }
    
    public static IRubyObject invokeAs(ThreadContext context, RubyClass asClass, IRubyObject self, String name, IRubyObject arg0, IRubyObject arg1, Block block) {
        return asClass.finvoke(context, self, name, arg0, arg1, block);
    }
    
    public static IRubyObject invokeAs(ThreadContext context, RubyClass asClass, IRubyObject self, String name, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, Block block) {
        return asClass.finvoke(context, self, name, arg0, arg1, arg2, block);
    }

    /**
     * The protocol for super method invocation is a bit complicated
     * in Ruby. In real terms it involves first finding the real
     * implementation class (the super class), getting the name of the
     * method to call from the frame, and then invoke that on the
     * super class with the current self as the actual object
     * invoking.
     */
    public static IRubyObject invokeSuper(ThreadContext context, IRubyObject self, IRubyObject[] args, Block block) {
        RubyModule klazz = context.getFrameKlazz();

        RubyClass superClass = findImplementerIfNecessary(self.getMetaClass(), klazz).getSuperClass();
        
        if (superClass == null) {
            String name = context.getFrameName(); 
            return callMethodMissing(context, self, klazz.searchMethod(name), name, args, CallType.SUPER, block);
        }
        return invokeAs(context, superClass, self, context.getFrameName(), args, block);
    }
    
    public static IRubyObject invokeSuper(ThreadContext context, IRubyObject self, Block block) {
        RubyModule klazz = context.getFrameKlazz();

        RubyClass superClass = findImplementerIfNecessary(self.getMetaClass(), klazz).getSuperClass();
        
        if (superClass == null) {
            String name = context.getFrameName(); 
            return callMethodMissing(context, self, klazz.searchMethod(name), name, CallType.SUPER, block);
        }
        return invokeAs(context, superClass, self, context.getFrameName(), block);
    }
    
    public static IRubyObject invokeSuper(ThreadContext context, IRubyObject self, IRubyObject arg0, Block block) {
        RubyModule klazz = context.getFrameKlazz();

        RubyClass superClass = findImplementerIfNecessary(self.getMetaClass(), klazz).getSuperClass();
        
        if (superClass == null) {
            String name = context.getFrameName(); 
            return callMethodMissing(context, self, klazz.searchMethod(name), name, arg0, CallType.SUPER, block);
        }
        return invokeAs(context, superClass, self, context.getFrameName(), arg0, block);
    }
    
    public static IRubyObject invokeSuper(ThreadContext context, IRubyObject self, IRubyObject arg0, IRubyObject arg1, Block block) {
        RubyModule klazz = context.getFrameKlazz();

        RubyClass superClass = findImplementerIfNecessary(self.getMetaClass(), klazz).getSuperClass();
        
        if (superClass == null) {
            String name = context.getFrameName(); 
            return callMethodMissing(context, self, klazz.searchMethod(name), name, arg0, arg1, CallType.SUPER, block);
        }
        return invokeAs(context, superClass, self, context.getFrameName(), arg0, arg1, block);
    }
    
    public static IRubyObject invokeSuper(ThreadContext context, IRubyObject self, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, Block block) {
        RubyModule klazz = context.getFrameKlazz();

        RubyClass superClass = findImplementerIfNecessary(self.getMetaClass(), klazz).getSuperClass();
        
        if (superClass == null) {
            String name = context.getFrameName(); 
            return callMethodMissing(context, self, klazz.searchMethod(name), name, arg0, arg1, arg2, CallType.SUPER, block);
        }
        return invokeAs(context, superClass, self, context.getFrameName(), arg0, arg1, arg2, block);
    }

    public static RubyArray ensureRubyArray(IRubyObject value) {
        return ensureRubyArray(value.getRuntime(), value);
    }

    public static RubyArray ensureRubyArray(Ruby runtime, IRubyObject value) {
        return value instanceof RubyArray ? (RubyArray)value : RubyArray.newArray(runtime, value);
    }

    public static RubyArray ensureMultipleAssignableRubyArray(IRubyObject value, Ruby runtime, boolean masgnHasHead) {
        if (!(value instanceof RubyArray)) {
            value = ArgsUtil.convertToRubyArray(runtime, value, masgnHasHead);
        }
        return (RubyArray) value;
    }
    
    public static IRubyObject fetchClassVariable(ThreadContext context, Ruby runtime, 
            IRubyObject self, String name) {
        RubyModule rubyClass = ASTInterpreter.getClassVariableBase(context, runtime);
   
        if (rubyClass == null) rubyClass = self.getMetaClass();

        return rubyClass.getClassVar(name);
    }
    
    public static IRubyObject fastFetchClassVariable(ThreadContext context, Ruby runtime, 
            IRubyObject self, String internedName) {
        RubyModule rubyClass = ASTInterpreter.getClassVariableBase(context, runtime);
   
        if (rubyClass == null) rubyClass = self.getMetaClass();

        return rubyClass.fastGetClassVar(internedName);
    }
    
    public static IRubyObject getConstant(ThreadContext context, String internedName) {
        Ruby runtime = context.getRuntime();

        return context.getCurrentScope().getStaticScope().getConstantWithConstMissing(runtime, internedName, runtime.getObject());
    }
    
    public static IRubyObject nullToNil(IRubyObject value, Ruby runtime) {
        return value != null ? value : runtime.getNil();
    }
    
    public static RubyClass prepareSuperClass(Ruby runtime, IRubyObject rubyClass) {
        RubyClass.checkInheritable(rubyClass); // use the same logic as in EvaluationState
        return (RubyClass)rubyClass;
    }
    
    public static RubyModule prepareClassNamespace(ThreadContext context, IRubyObject rubyModule) {
        if (rubyModule == null || rubyModule.isNil()) { // the isNil check should go away since class nil::Foo;end is not supposed be correct
            rubyModule = context.getCurrentScope().getStaticScope().getModule();

            if (rubyModule == null) {
                throw context.getRuntime().newTypeError("no outer class/module");
            }
        }

        if (rubyModule instanceof RubyModule) {
            return (RubyModule)rubyModule;
        } else {
            throw context.getRuntime().newTypeError(rubyModule + " is not a class/module");
        }
    }
    
    public static IRubyObject setClassVariable(ThreadContext context, Ruby runtime, 
            IRubyObject self, String name, IRubyObject value) {
        RubyModule rubyClass = ASTInterpreter.getClassVariableBase(context, runtime);
   
        if (rubyClass == null) rubyClass = self.getMetaClass();

        rubyClass.setClassVar(name, value);
   
        return value;
    }
    
    public static IRubyObject fastSetClassVariable(ThreadContext context, Ruby runtime, 
            IRubyObject self, String internedName, IRubyObject value) {
        RubyModule rubyClass = ASTInterpreter.getClassVariableBase(context, runtime);
   
        if (rubyClass == null) rubyClass = self.getMetaClass();

        rubyClass.fastSetClassVar(internedName, value);
   
        return value;
    }
    
    public static IRubyObject declareClassVariable(ThreadContext context, Ruby runtime, IRubyObject self, String name, IRubyObject value) {
        // FIXME: This isn't quite right; it shouldn't evaluate the value if it's going to throw the error
        RubyModule rubyClass = ASTInterpreter.getClassVariableBase(context, runtime);
   
        if (rubyClass == null) throw runtime.newTypeError("no class/module to define class variable");
        
        rubyClass.setClassVar(name, value);
   
        return value;
    }
    
    public static IRubyObject fastDeclareClassVariable(ThreadContext context, Ruby runtime, IRubyObject self, String internedName, IRubyObject value) {
        // FIXME: This isn't quite right; it shouldn't evaluate the value if it's going to throw the error
        RubyModule rubyClass = ASTInterpreter.getClassVariableBase(context, runtime);
   
        if (rubyClass == null) throw runtime.newTypeError("no class/module to define class variable");
        
        rubyClass.fastSetClassVar(internedName, value);
   
        return value;
    }
    
    public static void handleArgumentSizes(ThreadContext context, Ruby runtime, int given, int required, int opt, int rest) {
        if (opt == 0) {
            if (rest < 0) {
                // no opt, no rest, exact match
                if (given != required) {
                    throw runtime.newArgumentError("wrong # of arguments(" + given + " for " + required + ")");
                }
            } else {
                // only rest, must be at least required
                if (given < required) {
                    throw runtime.newArgumentError("wrong # of arguments(" + given + " for " + required + ")");
                }
            }
        } else {
            if (rest < 0) {
                // opt but no rest, must be at least required and no more than required + opt
                if (given < required) {
                    throw runtime.newArgumentError("wrong # of arguments(" + given + " for " + required + ")");
                } else if (given > (required + opt)) {
                    throw runtime.newArgumentError("wrong # of arguments(" + given + " for " + (required + opt) + ")");
                }
            } else {
                // opt and rest, must be at least required
                if (given < required) {
                    throw runtime.newArgumentError("wrong # of arguments(" + given + " for " + required + ")");
                }
            }
        }
    }
    
    /**
     * If it's Redo, Next, or Break, rethrow it as a normal exception for while to handle
     * @param re
     * @param runtime
     */
    public static Throwable unwrapRedoNextBreakOrJustLocalJump(RaiseException re, ThreadContext context) {
        RubyException exception = re.getException();
        if (context.getRuntime().getLocalJumpError().isInstance(exception)) {
            RubyLocalJumpError jumpError = (RubyLocalJumpError)re.getException();

            switch (jumpError.getReason()) {
            case REDO:
                return JumpException.REDO_JUMP;
            case NEXT:
                return new JumpException.NextJump(jumpError.exit_value());
            case BREAK:
                return new JumpException.BreakJump(context.getFrameJumpTarget(), jumpError.exit_value());
            }
        }
        return re;
    }
    
    public static String getLocalJumpTypeOrRethrow(RaiseException re) {
        RubyException exception = re.getException();
        Ruby runtime = exception.getRuntime();
        if (runtime.getLocalJumpError().isInstance(exception)) {
            RubyLocalJumpError jumpError = (RubyLocalJumpError)re.getException();

            IRubyObject reason = jumpError.reason();

            return reason.asJavaString();
        }

        throw re;
    }
    
    public static IRubyObject unwrapLocalJumpErrorValue(RaiseException re) {
        return ((RubyLocalJumpError)re.getException()).exit_value();
    }
    
    public static IRubyObject processBlockArgument(Ruby runtime, Block block) {
        if (!block.isGiven()) {
            return runtime.getNil();
        }
        
        return processGivenBlock(block, runtime);
    }

    private static IRubyObject processGivenBlock(Block block, Ruby runtime) {
        RubyProc blockArg = block.getProcObject();

        if (blockArg == null) {
            blockArg = runtime.newBlockPassProc(Block.Type.PROC, block);
            blockArg.getBlock().type = Block.Type.PROC;
        }

        return blockArg;
    }
    
    public static Block getBlockFromBlockPassBody(Ruby runtime, IRubyObject proc, Block currentBlock) {
        // No block from a nil proc
        if (proc.isNil()) return Block.NULL_BLOCK;

        // If not already a proc then we should try and make it one.
        if (!(proc instanceof RubyProc)) {
            proc = coerceProc(proc, runtime);
        }

        return getBlockFromProc(currentBlock, proc);
    }

    private static IRubyObject coerceProc(IRubyObject proc, Ruby runtime) throws RaiseException {
        proc = TypeConverter.convertToType(proc, runtime.getProc(), "to_proc", false);

        if (!(proc instanceof RubyProc)) {
            throw runtime.newTypeError("wrong argument type " + proc.getMetaClass().getName() + " (expected Proc)");
        }
        return proc;
    }

    private static Block getBlockFromProc(Block currentBlock, IRubyObject proc) {
        // TODO: Add safety check for taintedness
        if (currentBlock != null && currentBlock.isGiven()) {
            RubyProc procObject = currentBlock.getProcObject();
            // The current block is already associated with proc.  No need to create a new one
            if (procObject != null && procObject == proc) {
                return currentBlock;
            }
        }

        return ((RubyProc) proc).getBlock();       
    }
    
    public static Block getBlockFromBlockPassBody(IRubyObject proc, Block currentBlock) {
        return getBlockFromBlockPassBody(proc.getRuntime(), proc, currentBlock);

    }
    
    public static IRubyObject backref(ThreadContext context) {
        IRubyObject backref = context.getCurrentFrame().getBackRef();
        
        if(backref instanceof RubyMatchData) {
            ((RubyMatchData)backref).use();
        }
        return backref;
    }
    
    public static IRubyObject backrefLastMatch(ThreadContext context) {
        IRubyObject backref = context.getCurrentFrame().getBackRef();
        
        return RubyRegexp.last_match(backref);
    }
    
    public static IRubyObject backrefMatchPre(ThreadContext context) {
        IRubyObject backref = context.getCurrentFrame().getBackRef();
        
        return RubyRegexp.match_pre(backref);
    }
    
    public static IRubyObject backrefMatchPost(ThreadContext context) {
        IRubyObject backref = context.getCurrentFrame().getBackRef();
        
        return RubyRegexp.match_post(backref);
    }
    
    public static IRubyObject backrefMatchLast(ThreadContext context) {
        IRubyObject backref = context.getCurrentFrame().getBackRef();
        
        return RubyRegexp.match_last(backref);
    }
    
    public static IRubyObject callZSuper(Ruby runtime, ThreadContext context, Block block, IRubyObject self) {
        checkSuperDisabledOrOutOfMethod(context);

        // Has the method that is calling super received a block argument
        if (!block.isGiven()) block = context.getCurrentFrame().getBlock(); 
        
        return RuntimeHelpers.invokeSuper(context, self, context.getCurrentScope().getArgValues(), block);
    }
    
    public static IRubyObject[] appendToObjectArray(IRubyObject[] array, IRubyObject add) {
        IRubyObject[] newArray = new IRubyObject[array.length + 1];
        System.arraycopy(array, 0, newArray, 0, array.length);
        newArray[array.length] = add;
        return newArray;
    }
    
    public static JumpException.ReturnJump returnJump(IRubyObject result, ThreadContext context) {
        return context.returnJump(result);
    }
    
    public static IRubyObject breakJumpInWhile(JumpException.BreakJump bj, ThreadContext context) {
        // JRUBY-530, while case
        if (bj.getTarget() == context.getFrameJumpTarget()) {
            return (IRubyObject) bj.getValue();
        }

        throw bj;
    }
    
    public static IRubyObject breakJump(ThreadContext context, IRubyObject value) {
        throw new JumpException.BreakJump(context.getFrameJumpTarget(), value);
    }
    
    public static IRubyObject breakLocalJumpError(Ruby runtime, IRubyObject value) {
        throw runtime.newLocalJumpError(RubyLocalJumpError.Reason.BREAK, value, "unexpected break");
    }
    
    public static IRubyObject[] concatObjectArrays(IRubyObject[] array, IRubyObject[] add) {
        IRubyObject[] newArray = new IRubyObject[array.length + add.length];
        System.arraycopy(array, 0, newArray, 0, array.length);
        System.arraycopy(add, 0, newArray, array.length, add.length);
        return newArray;
    }
    
    public static IRubyObject isExceptionHandled(RubyException currentException, IRubyObject[] exceptions, Ruby runtime, ThreadContext context, IRubyObject self) {
        for (int i = 0; i < exceptions.length; i++) {
            if (!runtime.getModule().isInstance(exceptions[i])) {
                throw runtime.newTypeError("class or module required for rescue clause");
            }
            IRubyObject result = exceptions[i].callMethod(context, "===", currentException);
            if (result.isTrue()) return result;
        }
        return runtime.getFalse();
    }
    
    public static IRubyObject isJavaExceptionHandled(Exception currentException, IRubyObject[] exceptions, Ruby runtime, ThreadContext context, IRubyObject self) {
        if (currentException instanceof RaiseException) {
            return isExceptionHandled(((RaiseException)currentException).getException(), exceptions, runtime, context, self);
        } else {
            for (int i = 0; i < exceptions.length; i++) {
                if (exceptions[i] instanceof RubyClass) {
                    RubyClass rubyClass = (RubyClass)exceptions[i];
                    JavaClass javaClass = (JavaClass)rubyClass.fastGetInstanceVariable("@java_class");
                    if (javaClass != null) {
                        Class cls = javaClass.javaClass();
                        if (cls.isInstance(currentException)) {
                            return runtime.getTrue();
                        }
                    }
                }
            }

            return runtime.getFalse();
        }
    }

    public static void storeExceptionInErrorInfo(Exception currentException, ThreadContext context) {
        IRubyObject exception = null;
        if (currentException instanceof RaiseException) {
            exception = ((RaiseException)currentException).getException();
        } else {
            exception = JavaUtil.convertJavaToUsableRubyObject(context.getRuntime(), currentException);
        }
        context.setErrorInfo(exception);
    }

    public static void clearErrorInfo(ThreadContext context) {
        context.setErrorInfo(context.getRuntime().getNil());
    }
    
    public static void checkSuperDisabledOrOutOfMethod(ThreadContext context) {
        if (context.getFrameKlazz() == null) {
            String name = context.getFrameName();
            if (name != null) {
                throw context.getRuntime().newNameError("superclass method '" + name + "' disabled", name);
            } else {
                throw context.getRuntime().newNoMethodError("super called outside of method", null, context.getRuntime().getNil());
            }
        }
    }
    
    public static Block ensureSuperBlock(Block given, Block parent) {
        if (!given.isGiven()) {
            return parent;
        }
        return given;
    }
    
    public static RubyModule findImplementerIfNecessary(RubyModule clazz, RubyModule implementationClass) {
        if (implementationClass != null && implementationClass.needsImplementer()) {
            // modules are included with a shim class; we must find that shim to handle super() appropriately
            return clazz.findImplementer(implementationClass);
        } else {
            // classes are directly in the hierarchy, so no special logic is necessary for implementer
            return implementationClass;
        }
    }
    
    public static RubyArray createSubarray(RubyArray input, int start) {
        return (RubyArray)input.subseqLight(start, input.size() - start);
    }
    
    public static RubyArray createSubarray(IRubyObject[] input, Ruby runtime, int start) {
        return RubyArray.newArrayNoCopy(runtime, input, start);
    }
    
    public static RubyBoolean isWhenTriggered(IRubyObject expression, IRubyObject expressionsObject, ThreadContext context) {
        RubyArray expressions = RuntimeHelpers.splatValue(expressionsObject);
        for (int j = 0,k = expressions.getLength(); j < k; j++) {
            IRubyObject condition = expressions.eltInternal(j);

            if ((expression != null && condition.callMethod(context, "===", expression)
                    .isTrue())
                    || (expression == null && condition.isTrue())) {
                return context.getRuntime().getTrue();
            }
        }
        
        return context.getRuntime().getFalse();
    }
    
    public static IRubyObject setConstantInModule(IRubyObject module, IRubyObject value, String name, ThreadContext context) {
        return context.setConstantInModule(name, module, value);
    }
    
    public static IRubyObject retryJump() {
        throw JumpException.RETRY_JUMP;
    }
    
    public static IRubyObject redoJump() {
        throw JumpException.REDO_JUMP;
    }
    
    public static IRubyObject redoLocalJumpError(Ruby runtime) {
        throw runtime.newLocalJumpError(RubyLocalJumpError.Reason.REDO, runtime.getNil(), "unexpected redo");
    }
    
    public static IRubyObject nextJump(IRubyObject value) {
        throw new JumpException.NextJump(value);
    }
    
    public static IRubyObject nextLocalJumpError(Ruby runtime, IRubyObject value) {
        throw runtime.newLocalJumpError(RubyLocalJumpError.Reason.NEXT, value, "unexpected next");
    }
    
    public static final int MAX_SPECIFIC_ARITY_OBJECT_ARRAY = 5;
    
    public static IRubyObject[] constructObjectArray(IRubyObject one) {
        return new IRubyObject[] {one};
    }
    
    public static IRubyObject[] constructObjectArray(IRubyObject one, IRubyObject two) {
        return new IRubyObject[] {one, two};
    }
    
    public static IRubyObject[] constructObjectArray(IRubyObject one, IRubyObject two, IRubyObject three) {
        return new IRubyObject[] {one, two, three};
    }
    
    public static IRubyObject[] constructObjectArray(IRubyObject one, IRubyObject two, IRubyObject three, IRubyObject four) {
        return new IRubyObject[] {one, two, three, four};
    }
    
    public static IRubyObject[] constructObjectArray(IRubyObject one, IRubyObject two, IRubyObject three, IRubyObject four, IRubyObject five) {
        return new IRubyObject[] {one, two, three, four, five};
    }
    
    public static String[] constructStringArray(String one) {
        return new String[] {one};
    }
    
    public static String[] constructStringArray(String one, String two) {
        return new String[] {one, two};
    }
    
    public static String[] constructStringArray(String one, String two, String three) {
        return new String[] {one, two, three};
    }
    
    public static String[] constructStringArray(String one, String two, String three, String four) {
        return new String[] {one, two, three, four};
    }
    
    public static String[] constructStringArray(String one, String two, String three, String four, String five) {
        return new String[] {one, two, three, four, five};
    }
    
    public static String[] constructStringArray(String one, String two, String three, String four, String five, String six) {
        return new String[] {one, two, three, four, five, six};
    }
    
    public static String[] constructStringArray(String one, String two, String three, String four, String five, String six, String seven) {
        return new String[] {one, two, three, four, five, six, seven};
    }
    
    public static String[] constructStringArray(String one, String two, String three, String four, String five, String six, String seven, String eight) {
        return new String[] {one, two, three, four, five, six, seven, eight};
    }
    
    public static String[] constructStringArray(String one, String two, String three, String four, String five, String six, String seven, String eight, String nine) {
        return new String[] {one, two, three, four, five, six, seven, eight, nine};
    }
    
    public static String[] constructStringArray(String one, String two, String three, String four, String five, String six, String seven, String eight, String nine, String ten) {
        return new String[] {one, two, three, four, five, six, seven, eight, nine, ten};
    }
    
    public static final int MAX_SPECIFIC_ARITY_HASH = 3;
    
    public static RubyHash constructHash(Ruby runtime, IRubyObject key1, IRubyObject value1) {
        RubyHash hash = RubyHash.newHash(runtime);
        hash.fastASet(key1, value1);
        return hash;
    }
    
    public static RubyHash constructHash(Ruby runtime, IRubyObject key1, IRubyObject value1, IRubyObject key2, IRubyObject value2) {
        RubyHash hash = RubyHash.newHash(runtime);
        hash.fastASet(key1, value1);
        hash.fastASet(key2, value2);
        return hash;
    }
    
    public static RubyHash constructHash(Ruby runtime, IRubyObject key1, IRubyObject value1, IRubyObject key2, IRubyObject value2, IRubyObject key3, IRubyObject value3) {
        RubyHash hash = RubyHash.newHash(runtime);
        hash.fastASet(key1, value1);
        hash.fastASet(key2, value2);
        hash.fastASet(key3, value3);
        return hash;
    }
    
    public static IRubyObject defineAlias(ThreadContext context, String newName, String oldName) {
        Ruby runtime = context.getRuntime();
        RubyModule module = context.getRubyClass();
   
        if (module == null) throw runtime.newTypeError("no class to make alias");
   
        module.defineAlias(newName, oldName);
        module.callMethod(context, "method_added", runtime.newSymbol(newName));
   
        return runtime.getNil();
    }
    
    public static IRubyObject negate(IRubyObject value, Ruby runtime) {
        if (value.isTrue()) return runtime.getFalse();
        return runtime.getTrue();
    }
    
    public static IRubyObject stringOrNil(String value, Ruby runtime, IRubyObject nil) {
        if (value == null) return nil;
        return RubyString.newString(runtime, value);
    }
    
    public static void preLoad(ThreadContext context, String[] varNames) {
        StaticScope staticScope = new LocalStaticScope(null, varNames);
        staticScope.setModule(context.getRuntime().getObject());
        DynamicScope scope = DynamicScope.newDynamicScope(staticScope);
        
        // Each root node has a top-level scope that we need to push
        context.preScopedBody(scope);
    }
    
    public static void postLoad(ThreadContext context) {
        context.postScopedBody();
    }
    
    public static void registerEndBlock(Block block, Ruby runtime) {
        runtime.pushExitBlock(runtime.newProc(Block.Type.LAMBDA, block));
    }
    
    public static IRubyObject match3(RubyRegexp regexp, IRubyObject value, ThreadContext context) {
        if (value instanceof RubyString) {
            return regexp.op_match(context, value);
        } else {
            return value.callMethod(context, "=~", regexp);
        }
    }
    
    public static IRubyObject getErrorInfo(Ruby runtime) {
        return runtime.getGlobalVariables().get("$!");
    }
    
    public static void setErrorInfo(Ruby runtime, IRubyObject error) {
        runtime.getGlobalVariables().set("$!", error);
    }

    public static IRubyObject setLastLine(Ruby runtime, ThreadContext context, IRubyObject value) {
        return context.getCurrentFrame().setLastLine(value);
    }

    public static IRubyObject getLastLine(Ruby runtime, ThreadContext context) {
        return context.getCurrentFrame().getLastLine();
    }

    public static IRubyObject setBackref(Ruby runtime, ThreadContext context, IRubyObject value) {
        if (!value.isNil() && !(value instanceof RubyMatchData)) throw runtime.newTypeError(value, runtime.getMatchData());
        return context.getCurrentFrame().setBackRef(value);
    }

    public static IRubyObject getBackref(Ruby runtime, ThreadContext context) {
        IRubyObject backref = context.getCurrentFrame().getBackRef();
        if (backref instanceof RubyMatchData) ((RubyMatchData)backref).use();
        return backref;
    }
    
    public static IRubyObject preOpAsgnWithOrAnd(IRubyObject receiver, ThreadContext context, IRubyObject self, CallSite varSite) {
        return varSite.call(context, self, receiver);
    }
    
    public static IRubyObject postOpAsgnWithOrAnd(IRubyObject receiver, IRubyObject value, ThreadContext context, IRubyObject self, CallSite varAsgnSite) {
        varAsgnSite.call(context, self, receiver, value);
        return value;
    }
    
    public static IRubyObject opAsgnWithMethod(ThreadContext context, IRubyObject self, IRubyObject receiver, IRubyObject arg, CallSite varSite, CallSite opSite, CallSite opAsgnSite) {
        IRubyObject var = varSite.call(context, self, receiver);
        IRubyObject result = opSite.call(context, self, var, arg);
        opAsgnSite.call(context, self, receiver, result);

        return result;
    }
    
    public static IRubyObject opElementAsgnWithMethod(ThreadContext context, IRubyObject self, IRubyObject receiver, IRubyObject value, CallSite elementSite, CallSite opSite, CallSite elementAsgnSite) {
        IRubyObject var = elementSite.call(context, self, receiver);
        IRubyObject result = opSite.call(context, self, var, value);
        elementAsgnSite.call(context, self, receiver, result);

        return result;
    }
    
    public static IRubyObject opElementAsgnWithMethod(ThreadContext context, IRubyObject self, IRubyObject receiver, IRubyObject arg, IRubyObject value, CallSite elementSite, CallSite opSite, CallSite elementAsgnSite) {
        IRubyObject var = elementSite.call(context, self, receiver, arg);
        IRubyObject result = opSite.call(context, self, var, value);
        elementAsgnSite.call(context, self, receiver, arg, result);

        return result;
    }
    
    public static IRubyObject opElementAsgnWithMethod(ThreadContext context, IRubyObject self, IRubyObject receiver, IRubyObject arg1, IRubyObject arg2, IRubyObject value, CallSite elementSite, CallSite opSite, CallSite elementAsgnSite) {
        IRubyObject var = elementSite.call(context, self, receiver, arg1, arg2);
        IRubyObject result = opSite.call(context, self, var, value);
        elementAsgnSite.call(context, self, receiver, arg1, arg2, result);

        return result;
    }
    
    public static IRubyObject opElementAsgnWithMethod(ThreadContext context, IRubyObject self, IRubyObject receiver, IRubyObject arg1, IRubyObject arg2, IRubyObject arg3, IRubyObject value, CallSite elementSite, CallSite opSite, CallSite elementAsgnSite) {
        IRubyObject var = elementSite.call(context, self, receiver, arg1, arg2, arg3);
        IRubyObject result = opSite.call(context, self, var, value);
        elementAsgnSite.call(context, self, receiver, new IRubyObject[] {arg1, arg2, arg3, result});

        return result;
    }
    
    public static IRubyObject opElementAsgnWithMethod(ThreadContext context, IRubyObject self, IRubyObject receiver, IRubyObject[] args, IRubyObject value, CallSite elementSite, CallSite opSite, CallSite elementAsgnSite) {
        IRubyObject var = elementSite.call(context, self, receiver);
        IRubyObject result = opSite.call(context, self, var, value);
        elementAsgnSite.call(context, self, receiver, appendToObjectArray(args, result));

        return result;
    }

    
    public static IRubyObject opElementAsgnWithOrPartTwoOneArg(ThreadContext context, IRubyObject self, IRubyObject receiver, IRubyObject arg, IRubyObject value, CallSite asetSite) {
        asetSite.call(context, self, receiver, arg, value);
        return value;
    }
    
    public static IRubyObject opElementAsgnWithOrPartTwoTwoArgs(ThreadContext context, IRubyObject self, IRubyObject receiver, IRubyObject[] args, IRubyObject value, CallSite asetSite) {
        asetSite.call(context, self, receiver, args[0], args[1], value);
        return value;
    }
    
    public static IRubyObject opElementAsgnWithOrPartTwoThreeArgs(ThreadContext context, IRubyObject self, IRubyObject receiver, IRubyObject[] args, IRubyObject value, CallSite asetSite) {
        asetSite.call(context, self, receiver, new IRubyObject[] {args[0], args[1], args[2], value});
        return value;
    }
    
    public static IRubyObject opElementAsgnWithOrPartTwoNArgs(ThreadContext context, IRubyObject self, IRubyObject receiver, IRubyObject[] args, IRubyObject value, CallSite asetSite) {
        IRubyObject[] newArgs = new IRubyObject[args.length + 1];
        System.arraycopy(args, 0, newArgs, 0, args.length);
        newArgs[args.length] = value;
        asetSite.call(context, self, receiver, newArgs);
        return value;
    }

    public static RubyArray arrayValue(IRubyObject value) {
        IRubyObject tmp = value.checkArrayType();

        if (tmp.isNil()) {
            // Object#to_a is obsolete.  We match Ruby's hack until to_a goes away.  Then we can 
            // remove this hack too.
            Ruby runtime = value.getRuntime();
            
            if (value.getMetaClass().searchMethod("to_a").getImplementationClass() != runtime.getKernel()) {
                value = value.callMethod(runtime.getCurrentContext(), "to_a");
                if (!(value instanceof RubyArray)) throw runtime.newTypeError("`to_a' did not return Array");
                return (RubyArray)value;
            } else {
                return runtime.newArray(value);
            }
        }
        return (RubyArray)tmp;
    }

    public static IRubyObject aryToAry(IRubyObject value) {
        if (value instanceof RubyArray) return value;

        if (value.respondsTo("to_ary")) {
            return TypeConverter.convertToType(value, value.getRuntime().getArray(), "to_ary", false);
        }

        return value.getRuntime().newArray(value);
    }

    public static IRubyObject aValueSplat(IRubyObject value) {
        if (!(value instanceof RubyArray) || ((RubyArray) value).length().getLongValue() == 0) {
            return value.getRuntime().getNil();
        }

        RubyArray array = (RubyArray) value;

        return array.getLength() == 1 ? array.first() : array;
    }

    public static RubyArray splatValue(IRubyObject value) {
        if (value.isNil()) {
            return value.getRuntime().newArray(value);
        }

        return arrayValue(value);
    }

    public static void addInstanceMethod(RubyModule containingClass, String name, DynamicMethod method, Visibility visibility, ThreadContext context, Ruby runtime) {
        containingClass.addMethod(name, method);

        RubySymbol sym = runtime.fastNewSymbol(name);
        if (visibility == Visibility.MODULE_FUNCTION) {
            addModuleMethod(containingClass, name, method, context, sym);
        }

        callNormalMethodHook(containingClass, context, sym);
    }

    private static void addModuleMethod(RubyModule containingClass, String name, DynamicMethod method, ThreadContext context, RubySymbol sym) {
        containingClass.getSingletonClass().addMethod(name, new WrapperMethod(containingClass.getSingletonClass(), method, Visibility.PUBLIC));
        containingClass.callMethod(context, "singleton_method_added", sym);
    }

    private static void callNormalMethodHook(RubyModule containingClass, ThreadContext context, RubySymbol name) {
        // 'class << state.self' and 'class << obj' uses defn as opposed to defs
        if (containingClass.isSingleton()) {
            callSingletonMethodHook(((MetaClass) containingClass).getAttached(), context, name);
        } else {
            containingClass.callMethod(context, "method_added", name);
        }
    }

    private static void callSingletonMethodHook(IRubyObject receiver, ThreadContext context, RubySymbol name) {
        receiver.callMethod(context, "singleton_method_added", name);
    }

    private static DynamicMethod constructNormalMethod(String name, Visibility visibility, MethodFactory factory, RubyModule containingClass, String javaName, int arity, StaticScope scope, Object scriptObject, CallConfiguration callConfig) {
        DynamicMethod method;

        if (name.equals("initialize") || name.equals("initialize_copy") || visibility == Visibility.MODULE_FUNCTION) {
            visibility = Visibility.PRIVATE;
        }
        
        if (RubyInstanceConfig.LAZYHANDLES_COMPILE) {
            method = factory.getCompiledMethodLazily(containingClass, javaName, Arity.createArity(arity), visibility, scope, scriptObject, callConfig);
        } else {
            method = factory.getCompiledMethod(containingClass, javaName, Arity.createArity(arity), visibility, scope, scriptObject, callConfig);
        }

        return method;
    }

    private static DynamicMethod constructSingletonMethod(MethodFactory factory, RubyClass rubyClass, String javaName, int arity, StaticScope scope, Object scriptObject, CallConfiguration callConfig) {
        return factory.getCompiledMethodLazily(rubyClass, javaName, Arity.createArity(arity), Visibility.PUBLIC, scope, scriptObject, callConfig);
    }

    private static StaticScope creatScopeForClass(ThreadContext context, String[] scopeNames, int required, int optional, int rest) {

        StaticScope scope = new LocalStaticScope(context.getCurrentScope().getStaticScope(), scopeNames);
        scope.determineModule();
        scope.setArities(required, optional, rest);

        return scope;
    }

    private static void performNormalMethodChecks(RubyModule containingClass, Ruby runtime, String name) throws RaiseException {

        if (containingClass == runtime.getDummy()) {
            throw runtime.newTypeError("no class/module to add method");
        }

        if (containingClass == runtime.getObject() && name.equals("initialize")) {
            runtime.getWarnings().warn(ID.REDEFINING_DANGEROUS, "redefining Object#initialize may cause infinite loop", "Object#initialize");
        }

        if (name.equals("__id__") || name.equals("__send__")) {
            runtime.getWarnings().warn(ID.REDEFINING_DANGEROUS, "redefining `" + name + "' may cause serious problem", name);
        }
    }

    private static RubyClass performSingletonMethodChecks(Ruby runtime, IRubyObject receiver, String name) throws RaiseException {

        if (runtime.getSafeLevel() >= 4 && !receiver.isTaint()) {
            throw runtime.newSecurityError("Insecure; can't define singleton method.");
        }

        if (receiver instanceof RubyFixnum || receiver instanceof RubySymbol) {
            throw runtime.newTypeError("can't define singleton method \"" + name + "\" for " + receiver.getMetaClass().getBaseName());
        }

        if (receiver.isFrozen()) {
            throw runtime.newFrozenError("object");
        }
        
        RubyClass rubyClass = receiver.getSingletonClass();

        if (runtime.getSafeLevel() >= 4 && rubyClass.getMethods().get(name) != null) {
            throw runtime.newSecurityError("redefining method prohibited.");
        }
        
        return rubyClass;
    }
    
    public static IRubyObject arrayEntryOrNil(RubyArray array, IRubyObject nil, int index) {
        if (index < array.getLength()) {
            return array.entry(index);
        } else {
            return nil;
        }
    }
    
    public static RubyArray subarrayOrEmpty(RubyArray array, Ruby runtime, int index) {
        if (index < array.getLength()) {
            return createSubarray(array, index);
        } else {
            return RubyArray.newEmptyArray(runtime);
        }
    }
    
    public static RubyModule checkIsModule(IRubyObject maybeModule) {
        if (maybeModule instanceof RubyModule) return (RubyModule)maybeModule;
        
        throw maybeModule.getRuntime().newTypeError(maybeModule + " is not a class/module");
    }
    
    public static IRubyObject getGlobalVariable(Ruby runtime, String name) {
        return runtime.getGlobalVariables().get(name);
    }
    
    public static IRubyObject setGlobalVariable(IRubyObject value, Ruby runtime, String name) {
        return runtime.getGlobalVariables().set(name, value);
    }
    
    public static IRubyObject getInstanceVariable(IRubyObject self, Ruby runtime, String internedName) {
        IRubyObject result;
        
        if ((result = self.getInstanceVariables().fastGetInstanceVariable(internedName)) == null) {
            runtime.getWarnings().warning(ID.IVAR_NOT_INITIALIZED, "instance variable " + internedName + " not initialized");
            return runtime.getNil();
        }
        
        return result;
    }
    
    public static IRubyObject setInstanceVariable(IRubyObject value, IRubyObject self, String name) {
        return self.getInstanceVariables().fastSetInstanceVariable(name, value);
    }
}
