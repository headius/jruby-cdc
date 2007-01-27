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
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 Charles O Nutter <headius@headius.com>
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

import java.util.List;

import org.jruby.runtime.Block;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.marshal.MarshalStream;
import org.jruby.runtime.marshal.UnmarshalStream;
import org.jruby.util.IdUtil;
import org.jruby.exceptions.RaiseException;

/**
 * @author  jpetersen
 */
public class RubyStruct extends RubyObject {
    private IRubyObject[] values;

    /**
     * Constructor for RubyStruct.
     * @param runtime
     * @param rubyClass
     */
    public RubyStruct(IRuby runtime, RubyClass rubyClass) {
        super(runtime, rubyClass);
    }

    public static RubyClass createStructClass(IRuby runtime) {
        // TODO: NOT_ALLOCATABLE_ALLOCATOR may be ok here, but it's unclear how Structs
        // work with marshalling. Confirm behavior and ensure we're doing this correctly. JRUBY-415
        RubyClass structClass = runtime.defineClass("Struct", runtime.getObject(), ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        CallbackFactory callbackFactory = runtime.callbackFactory(RubyStruct.class);
        structClass.includeModule(runtime.getModule("Enumerable"));

        structClass.defineSingletonMethod("new", callbackFactory.getOptSingletonMethod("newInstance"));

        structClass.defineMethod("initialize", callbackFactory.getOptMethod("initialize"));
        structClass.defineMethod("clone", callbackFactory.getMethod("rbClone"));

        structClass.defineFastMethod("==", callbackFactory.getFastMethod("equal", IRubyObject.class));

        structClass.defineFastMethod("to_s", callbackFactory.getFastMethod("to_s"));
        structClass.defineFastMethod("inspect", callbackFactory.getFastMethod("inspect"));
        structClass.defineFastMethod("to_a", callbackFactory.getFastMethod("to_a"));
        structClass.defineFastMethod("values", callbackFactory.getFastMethod("to_a"));
        structClass.defineFastMethod("size", callbackFactory.getFastMethod("size"));
        structClass.defineFastMethod("length", callbackFactory.getFastMethod("size"));

        structClass.defineMethod("each", callbackFactory.getMethod("each"));
        structClass.defineFastMethod("[]", callbackFactory.getFastMethod("aref", IRubyObject.class));
        structClass.defineFastMethod("[]=", callbackFactory.getFastMethod("aset", IRubyObject.class, IRubyObject.class));

        structClass.defineFastMethod("members", callbackFactory.getFastMethod("members"));

        return structClass;
    }

    private static IRubyObject getInstanceVariable(RubyClass type, String name) {
        RubyClass structClass = type.getRuntime().getClass("Struct");

        while (type != null && type != structClass) {
            IRubyObject variable = type.getInstanceVariable(name);
            if (variable != null) {
                return variable;
            }

            type = type.getSuperClass();
        }

        return type.getRuntime().getNil();
    }

    private RubyClass classOf() {
        return getMetaClass() instanceof MetaClass ? getMetaClass().getSuperClass() : getMetaClass();
    }

    private void modify() {
        testFrozen("Struct is frozen");

        if (!isTaint() && getRuntime().getSafeLevel() >= 4) {
            throw getRuntime().newSecurityError("Insecure: can't modify struct");
        }
    }

    private IRubyObject setByName(String name, IRubyObject value) {
        RubyArray member = (RubyArray) getInstanceVariable(classOf(), "__member__");

        assert !member.isNil() : "uninitialized struct";

        modify();

        for (int i = 0; i < member.getLength(); i++) {
            if (member.entry(i).asSymbol().equals(name)) {
                return values[i] = value;
            }
        }

        throw notStructMemberError(name);
    }

    private IRubyObject getByName(String name) {
        RubyArray member = (RubyArray) getInstanceVariable(classOf(), "__member__");

        assert !member.isNil() : "uninitialized struct";

        for (int i = 0; i < member.getLength(); i++) {
            if (member.entry(i).asSymbol().equals(name)) {
                return values[i];
            }
        }

        throw notStructMemberError(name);
    }

    // Struct methods

    /** Create new Struct class.
     *
     * MRI: rb_struct_s_def / make_struct
     *
     */
    public static RubyClass newInstance(IRubyObject recv, IRubyObject[] args, Block block) {
        String name = null;
        IRuby runtime = recv.getRuntime();

        if (args.length > 0 && args[0] instanceof RubyString) {
            name = args[0].toString();
        }

        RubyArray member = recv.getRuntime().newArray();

        for (int i = name == null ? 0 : 1; i < args.length; i++) {
            member.append(RubySymbol.newSymbol(recv.getRuntime(), args[i].asSymbol()));
        }

        RubyClass newStruct;
        RubyClass superClass = (RubyClass)recv;

        if (name == null) {
            newStruct = new RubyClass(superClass, superClass.getAllocator());
        } else {
            if (!IdUtil.isConstant(name)) {
                throw runtime.newNameError("identifier " + name + " needs to be constant", name);
            }

            IRubyObject type = superClass.getConstantAt(name);

            if (type != null) {
                runtime.getWarnings().warn(runtime.getCurrentContext().getFramePosition(), "redefining constant Struct::" + name);
            }
            newStruct = superClass.newSubClass(name, superClass.getAllocator(), superClass.getCRef());
        }

        newStruct.setInstanceVariable("__size__", member.length());
        newStruct.setInstanceVariable("__member__", member);

        CallbackFactory callbackFactory = recv.getRuntime().callbackFactory(RubyStruct.class);
        newStruct.defineSingletonMethod("new", callbackFactory.getOptSingletonMethod("newStruct"));
        newStruct.defineSingletonMethod("[]", callbackFactory.getOptSingletonMethod("newStruct"));
        newStruct.defineSingletonMethod("members", callbackFactory.getSingletonMethod("members"));

        // define access methods.
        for (int i = name == null ? 0 : 1; i < args.length; i++) {
            String memberName = args[i].asSymbol();
            newStruct.defineMethod(memberName, callbackFactory.getMethod("get"));
            newStruct.defineMethod(memberName + "=", callbackFactory.getMethod("set", IRubyObject.class));
        }
        
        if (block != null) {
            block.yield(recv.getRuntime().getCurrentContext(), null, newStruct, newStruct, false);
        }

        return newStruct;
    }

    /** Create new Structure.
     *
     * MRI: struct_alloc
     *
     */
    public static RubyStruct newStruct(IRubyObject recv, IRubyObject[] args, Block block) {
        RubyStruct struct = new RubyStruct(recv.getRuntime(), (RubyClass) recv);

        int size = RubyNumeric.fix2int(getInstanceVariable((RubyClass) recv, "__size__"));

        struct.values = new IRubyObject[size];

        struct.callInit(args, block);

        return struct;
    }

    public IRubyObject initialize(IRubyObject[] args, Block unusedBlock) {
        modify();

        int size = RubyNumeric.fix2int(getInstanceVariable(getMetaClass(), "__size__"));

        if (args.length > size) {
            throw getRuntime().newArgumentError("struct size differs (" + args.length +" for " + size + ")");
        }

        for (int i = 0; i < args.length; i++) {
            values[i] = args[i];
        }

        for (int i = args.length; i < size; i++) {
            values[i] = getRuntime().getNil();
        }

        return getRuntime().getNil();
    }
    
    public static RubyArray members(IRubyObject recv, Block block) {
        RubyArray member = (RubyArray) getInstanceVariable((RubyClass) recv, "__member__");

        assert !member.isNil() : "uninitialized struct";

        RubyArray result = recv.getRuntime().newArray(member.getLength());
        for (int i = 0; i < member.getLength(); i++) {
            result.append(recv.getRuntime().newString(member.entry(i).asSymbol()));
        }

        return result;
    }

    public RubyArray members() {
        return members(classOf(), null);
    }

    public IRubyObject set(IRubyObject value, Block block) {
        String name = getRuntime().getCurrentContext().getFrameLastFunc();
        if (name.endsWith("=")) {
            name = name.substring(0, name.length() - 1);
        }

        RubyArray member = (RubyArray) getInstanceVariable(classOf(), "__member__");

        assert !member.isNil() : "uninitialized struct";

        modify();

        for (int i = 0; i < member.getLength(); i++) {
            if (member.entry(i).asSymbol().equals(name)) {
                return values[i] = value;
            }
        }

        throw notStructMemberError(name);
    }

    private RaiseException notStructMemberError(String name) {
        return getRuntime().newNameError(name + " is not struct member", name);
    }

    public IRubyObject get(Block block) {
        String name = getRuntime().getCurrentContext().getFrameLastFunc();

        RubyArray member = (RubyArray) getInstanceVariable(classOf(), "__member__");

        assert !member.isNil() : "uninitialized struct";

        for (int i = 0; i < member.getLength(); i++) {
            if (member.entry(i).asSymbol().equals(name)) {
                return values[i];
            }
        }

        throw notStructMemberError(name);
    }

    public IRubyObject rbClone(Block block) {
        RubyStruct clone = new RubyStruct(getRuntime(), getMetaClass());

        clone.values = new IRubyObject[values.length];
        System.arraycopy(values, 0, clone.values, 0, values.length);

        clone.setFrozen(this.isFrozen());
        clone.setTaint(this.isTaint());

        return clone;
    }

    public IRubyObject equal(IRubyObject other) {
        if (this == other) {
            return getRuntime().getTrue();
        } else if (!(other instanceof RubyStruct)) {
            return getRuntime().getFalse();
        } else if (getMetaClass() != other.getMetaClass()) {
            return getRuntime().getFalse();
        } else {
            for (int i = 0; i < values.length; i++) {
                if (!values[i].equals(((RubyStruct) other).values[i])) {
                    return getRuntime().getFalse();
                }
            }
            return getRuntime().getTrue();
        }
    }

    public IRubyObject to_s() {
        return inspect();
    }

    public IRubyObject inspect() {
        RubyArray member = (RubyArray) getInstanceVariable(classOf(), "__member__");

        assert !member.isNil() : "uninitialized struct";

        StringBuffer sb = new StringBuffer(100);

        sb.append("#<struct ").append(getMetaClass().getName()).append(' ');

        for (int i = 0; i < member.getLength(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(member.entry(i).asSymbol()).append("=");
            sb.append(values[i].callMethod(getRuntime().getCurrentContext(), "inspect"));
        }

        sb.append('>');

        return getRuntime().newString(sb.toString()); // OBJ_INFECT
    }

    public RubyArray to_a() {
        return getRuntime().newArray(values);
    }

    public RubyFixnum size() {
        return getRuntime().newFixnum(values.length);
    }

    public IRubyObject each(Block block) {
        ThreadContext context = getRuntime().getCurrentContext();
        for (int i = 0; i < values.length; i++) {
            context.yield(values[i], block);
        }

        return this;
    }

    public IRubyObject aref(IRubyObject key) {
        if (key instanceof RubyString || key instanceof RubySymbol) {
            return getByName(key.asSymbol());
        }

        int idx = RubyNumeric.fix2int(key);

        idx = idx < 0 ? values.length + idx : idx;

        if (idx < 0) {
            throw getRuntime().newIndexError("offset " + idx + " too large for struct (size:" + values.length + ")");
        } else if (idx >= values.length) {
            throw getRuntime().newIndexError("offset " + idx + " too large for struct (size:" + values.length + ")");
        }

        return values[idx];
    }

    public IRubyObject aset(IRubyObject key, IRubyObject value) {
        if (key instanceof RubyString || key instanceof RubySymbol) {
            return setByName(key.asSymbol(), value);
        }

        int idx = RubyNumeric.fix2int(key);

        idx = idx < 0 ? values.length + idx : idx;

        if (idx < 0) {
            throw getRuntime().newIndexError("offset " + idx + " too large for struct (size:" + values.length + ")");
        } else if (idx >= values.length) {
            throw getRuntime().newIndexError("offset " + idx + " too large for struct (size:" + values.length + ")");
        }

        modify();
        return values[idx] = value;
    }

    public void marshalTo(MarshalStream output) throws java.io.IOException {
        output.write('S');

        String className = getMetaClass().getName();
        if (className == null) {
            throw getRuntime().newArgumentError("can't dump anonymous class");
        }
        output.dumpObject(RubySymbol.newSymbol(getRuntime(), className));

        List members = ((RubyArray) getInstanceVariable(classOf(), "__member__")).getList();
        output.dumpInt(members.size());

        for (int i = 0; i < members.size(); i++) {
            RubySymbol name = (RubySymbol) members.get(i);
            output.dumpObject(name);
            output.dumpObject(values[i]);
        }
    }

    public static RubyStruct unmarshalFrom(UnmarshalStream input) throws java.io.IOException {
        IRuby runtime = input.getRuntime();

        RubySymbol className = (RubySymbol) input.unmarshalObject();
        RubyClass rbClass = pathToClass(runtime, className.asSymbol());
        if (rbClass == null) {
            throw runtime.newNameError("uninitialized constant " + className, className.asSymbol());
        }

        int size = input.unmarshalInt();

        IRubyObject[] values = new IRubyObject[size];
        for (int i = 0; i < size; i++) {
            input.unmarshalObject(); // Read and discard a Symbol, which is the name
            values[i] = input.unmarshalObject();
        }

        RubyStruct result = newStruct(rbClass, values, null);
        input.registerLinkTarget(result);
        return result;
    }

    private static RubyClass pathToClass(IRuby runtime, String path) {
        // FIXME: Throw the right ArgumentError's if the class is missing
        // or if it's a module.
        return (RubyClass) runtime.getClassFromPath(path);
    }
}
