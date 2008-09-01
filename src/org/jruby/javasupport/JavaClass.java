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
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004-2005 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2004 David Corbin <dcorbin@users.sourceforge.net>
 * Copyright (C) 2005 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2006 Kresten Krab Thorup <krab@gnu.org>
 * Copyright (C) 2007 Miguel Covarrubias <mlcovarrubias@gmail.com>
 * Copyright (C) 2007 William N Dortch <bill.dortch@gmail.com>
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
package org.jruby.javasupport;

import org.jruby.java.invokers.StaticFieldGetter;
import org.jruby.java.invokers.StaticMethodInvoker;
import org.jruby.java.invokers.InstanceFieldGetter;
import org.jruby.java.invokers.InstanceFieldSetter;
import org.jruby.java.invokers.InstanceMethodInvoker;
import org.jruby.java.invokers.StaticFieldSetter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyInteger;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.anno.JRubyMethod;
import org.jruby.anno.JRubyClass;
import org.jruby.common.IRubyWarnings.ID;
import org.jruby.exceptions.RaiseException;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.java.addons.ArrayJavaAddons;
import org.jruby.java.proxies.ArrayJavaProxy;
import org.jruby.java.invokers.ConstructorInvoker;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.CallType;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.jruby.util.ByteList;
import org.jruby.util.IdUtil;
import org.jruby.util.SafePropertyAccessor;

@JRubyClass(name="Java::JavaClass", parent="Java::JavaObject")
public class JavaClass extends JavaObject {

    // some null objects to simplify later code
    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[] {};
    private static final Method[] EMPTY_METHOD_ARRAY = new Method[] {};
    private static final Constructor[] EMPTY_CONSTRUCTOR_ARRAY = new Constructor[] {};
    private static final Field[] EMPTY_FIELD_ARRAY = new Field[] {};

    private static class AssignedName {
        // to override an assigned name, the type must be less than
        // or equal to the assigned type. so a field name in a subclass
        // will override an alias in a superclass, but not a method.
        static final int RESERVED = 0;
        static final int METHOD = 1;
        static final int FIELD = 2;
        static final int PROTECTED_METHOD = 3;
        static final int WEAKLY_RESERVED = 4; // we'll be peeved, but not devastated, if you override
        static final int ALIAS = 5;
        // yes, protected fields are weaker than aliases. many conflicts
        // in the old AWT code, for example, where you really want 'size'
        // to mean the public method getSize, not the protected field 'size'.
        static final int PROTECTED_FIELD = 6;
        String name;
        int type;
        AssignedName () {}
        AssignedName(String name, int type) {
            this.name = name;
            this.type = type;
        }
    }

    // TODO: other reserved names?
    private static final Map<String, AssignedName> RESERVED_NAMES = new HashMap<String, AssignedName>();
    static {
        RESERVED_NAMES.put("__id__", new AssignedName("__id__", AssignedName.RESERVED));
        RESERVED_NAMES.put("__send__", new AssignedName("__send__", AssignedName.RESERVED));
        RESERVED_NAMES.put("class", new AssignedName("class", AssignedName.RESERVED));
        RESERVED_NAMES.put("initialize", new AssignedName("initialize", AssignedName.RESERVED));
        RESERVED_NAMES.put("object_id", new AssignedName("object_id", AssignedName.RESERVED));
        RESERVED_NAMES.put("private", new AssignedName("private", AssignedName.RESERVED));
        RESERVED_NAMES.put("protected", new AssignedName("protected", AssignedName.RESERVED));
        RESERVED_NAMES.put("public", new AssignedName("public", AssignedName.RESERVED));

        // weakly reserved names
        RESERVED_NAMES.put("id", new AssignedName("id", AssignedName.WEAKLY_RESERVED));
    }
    private static final Map<String, AssignedName> STATIC_RESERVED_NAMES = new HashMap<String, AssignedName>(RESERVED_NAMES);
    static {
        STATIC_RESERVED_NAMES.put("new", new AssignedName("new", AssignedName.RESERVED));
    }
    private static final Map<String, AssignedName> INSTANCE_RESERVED_NAMES = new HashMap<String, AssignedName>(RESERVED_NAMES);

    private static abstract class NamedInstaller {
        static final int STATIC_FIELD = 1;
        static final int STATIC_METHOD = 2;
        static final int INSTANCE_FIELD = 3;
        static final int INSTANCE_METHOD = 4;
        static final int CONSTRUCTOR = 5;
        String name;
        int type;
        Visibility visibility = Visibility.PUBLIC;
        boolean isProtected;
        NamedInstaller () {}
        NamedInstaller (String name, int type) {
            this.name = name;
            this.type = type;
        }
        abstract void install(RubyModule proxy);
        // small hack to save a cast later on
        boolean hasLocalMethod() {
            return true;
        }
        boolean isPublic() {
            return visibility == Visibility.PUBLIC;
        }
        boolean isProtected() {
            return visibility == Visibility.PROTECTED;
        }
    }

    private static abstract class FieldInstaller extends NamedInstaller {
        Field field;
        FieldInstaller(){}
        FieldInstaller(String name, int type, Field field) {
            super(name,type);
            this.field = field;
        }
    }

    private static class StaticFieldGetterInstaller extends FieldInstaller {
        StaticFieldGetterInstaller(){}
        StaticFieldGetterInstaller(String name, Field field) {
            super(name,STATIC_FIELD,field);
        }
        void install(RubyModule proxy) {
            if (Modifier.isPublic(field.getModifiers())) {
                proxy.getSingletonClass().addMethod(name, new StaticFieldGetter(name, proxy, field));
            }
        }
    }

    private static class StaticFieldSetterInstaller extends FieldInstaller {
        StaticFieldSetterInstaller(){}
        StaticFieldSetterInstaller(String name, Field field) {
            super(name,STATIC_FIELD,field);
        }
        void install(RubyModule proxy) {
            if (Modifier.isPublic(field.getModifiers())) {
                proxy.getSingletonClass().addMethod(name, new StaticFieldSetter(name, proxy, field));
            }
        }
    }

    private static class InstanceFieldGetterInstaller extends FieldInstaller {
        InstanceFieldGetterInstaller(){}
        InstanceFieldGetterInstaller(String name, Field field) {
            super(name,INSTANCE_FIELD,field);
        }
        void install(RubyModule proxy) {
            if (Modifier.isPublic(field.getModifiers())) {
                proxy.addMethod(name, new InstanceFieldGetter(name, proxy, field));
            }
        }
    }

    private static class InstanceFieldSetterInstaller extends FieldInstaller {
        InstanceFieldSetterInstaller(){}
        InstanceFieldSetterInstaller(String name, Field field) {
            super(name,INSTANCE_FIELD,field);
        }
        void install(RubyModule proxy) {
            if (Modifier.isPublic(field.getModifiers())) {
                proxy.addMethod(name, new InstanceFieldSetter(name, proxy, field));
            }
        }
    }

    private static abstract class MethodInstaller extends NamedInstaller {
        private boolean haveLocalMethod;
        protected List<Method> methods;
        protected List<String> aliases;
        MethodInstaller(){}
        MethodInstaller(String name, int type) {
            super(name,type);
        }

        // called only by initializing thread; no synchronization required
        void addMethod(Method method, Class<?> javaClass) {
            if (methods == null) {
                methods = new ArrayList<Method>();
            }
            if (!Ruby.isSecurityRestricted()) {
                method.setAccessible(true);
            }
            methods.add(method);
            haveLocalMethod |= javaClass == method.getDeclaringClass();
        }

        // called only by initializing thread; no synchronization required
        void addAlias(String alias) {
            if (aliases == null) {
                aliases = new ArrayList<String>();
            }
            if (!aliases.contains(alias))
                aliases.add(alias);
        }

        // modified only by addMethod; no synchronization required
        boolean hasLocalMethod () {
            return haveLocalMethod;
        }
    }

    private static class ConstructorInvokerInstaller extends MethodInstaller {
        private boolean haveLocalConstructor;
        protected List<Constructor> constructors;
        
        ConstructorInvokerInstaller(String name) {
            super(name,STATIC_METHOD);
        }

        // called only by initializing thread; no synchronization required
        void addConstructor(Constructor ctor, Class<?> javaClass) {
            if (constructors == null) {
                constructors = new ArrayList<Constructor>();
            }
            if (!Ruby.isSecurityRestricted()) {
                ctor.setAccessible(true);
            }
            constructors.add(ctor);
            haveLocalConstructor |= javaClass == ctor.getDeclaringClass();
        }
        
        void install(RubyModule proxy) {
            if (haveLocalConstructor) {
                DynamicMethod method = new ConstructorInvoker(proxy, constructors);
                proxy.addMethod(name, method);
            }
        }
    }

    private static class StaticMethodInvokerInstaller extends MethodInstaller {
        StaticMethodInvokerInstaller(String name) {
            super(name,STATIC_METHOD);
        }

        void install(RubyModule proxy) {
            if (hasLocalMethod()) {
                RubyClass singleton = proxy.getSingletonClass();
                DynamicMethod method = new StaticMethodInvoker(singleton, methods);
                singleton.addMethod(name, method);
                if (aliases != null && isPublic() ) {
                    singleton.defineAliases(aliases, this.name);
                    aliases = null;
                }
            }
        }
    }

    private static class InstanceMethodInvokerInstaller extends MethodInstaller {
        InstanceMethodInvokerInstaller(String name) {
            super(name,INSTANCE_METHOD);
        }
        void install(RubyModule proxy) {
            if (hasLocalMethod()) {
                DynamicMethod method = new InstanceMethodInvoker(proxy, methods);
                proxy.addMethod(name, method);
                if (aliases != null && isPublic()) {
                    proxy.defineAliases(aliases, this.name);
                    aliases = null;
                }
            }
        }
    }

    private static class ConstantField {
        static final int CONSTANT = Modifier.FINAL | Modifier.PUBLIC | Modifier.STATIC;
        final Field field;
        ConstantField(Field field) {
            this.field = field;
        }
        void install(final RubyModule proxy) {
            if (proxy.fastGetConstantAt(field.getName()) == null) {
                // TODO: catch exception if constant is already set by other
                // thread
                if (!Ruby.isSecurityRestricted()) {
                    field.setAccessible(true);
                }
                try {
                    proxy.setConstant(field.getName(), JavaUtil.convertJavaToUsableRubyObject(proxy.getRuntime(), field.get(null)));
                } catch (IllegalAccessException iae) {
                    throw proxy.getRuntime().newTypeError(
                                        "illegal access on setting variable: " + iae.getMessage());
                }
            }
        }
        static boolean isConstant(final Field field) {
            return (field.getModifiers() & CONSTANT) == CONSTANT &&
                Character.isUpperCase(field.getName().charAt(0));
        }
    }
    
    private final RubyModule JAVA_UTILITIES = getRuntime().getJavaSupport().getJavaUtilitiesModule();
    
    private Map<String, AssignedName> staticAssignedNames;
    private Map<String, AssignedName> instanceAssignedNames;
    private Map<String, NamedInstaller> staticInstallers;
    private Map<String, NamedInstaller> instanceInstallers;
    private ConstructorInvokerInstaller constructorInstaller;
    private List<ConstantField> constantFields;
    // caching constructors, as they're accessed for each new instance
    private volatile RubyArray constructors;
    
    private volatile ArrayList<IRubyObject> proxyExtenders;

    // proxy module for interfaces
    private volatile RubyModule proxyModule;

    // proxy class for concrete classes.  also used for
    // "concrete" interfaces, which is why we have two fields
    private volatile RubyClass proxyClass;

    // readable only by thread building proxy, so don't need to be
    // volatile. used to handle recursive calls to getProxyClass/Module
    // while proxy is being constructed (usually when a constant
    // defined by a class is of the same type as that class).
    private RubyModule unfinishedProxyModule;
    private RubyClass unfinishedProxyClass;
    
    private final ReentrantLock proxyLock = new ReentrantLock();
    
    public RubyModule getProxyModule() {
        // allow proxy to be read without synchronization. if proxy
        // is under construction, only the building thread can see it.
        RubyModule proxy;
        if ((proxy = proxyModule) != null) {
            // proxy is complete, return it
            return proxy;
        } else if (proxyLock.isHeldByCurrentThread()) {
            // proxy is under construction, building thread can
            // safely read non-volatile value
            return unfinishedProxyModule; 
        }
        return null;
    }
    
    public RubyClass getProxyClass() {
        // allow proxy to be read without synchronization. if proxy
        // is under construction, only the building thread can see it.
        RubyClass proxy;
        if ((proxy = proxyClass) != null) {
            // proxy is complete, return it
            return proxy;
        } else if (proxyLock.isHeldByCurrentThread()) {
            // proxy is under construction, building thread can
            // safely read non-volatile value
            return unfinishedProxyClass; 
        }
        return null;
    }
    
    public void lockProxy() {
        proxyLock.lock();
    }
    
    public void unlockProxy() {
        proxyLock.unlock();
    }

    protected Map<String, AssignedName> getStaticAssignedNames() {
        return staticAssignedNames;
    }
    protected Map<String, AssignedName> getInstanceAssignedNames() {
        return instanceAssignedNames;
    }
    
    private JavaClass(Ruby runtime, Class<?> javaClass) {
        super(runtime, (RubyClass) runtime.getJavaSupport().getJavaClassClass(), javaClass);
        if (javaClass.isInterface()) {
            initializeInterface(javaClass);
        } else if (!(javaClass.isArray() || javaClass.isPrimitive())) {
            // TODO: public only?
            initializeClass(javaClass);
        }
    }
    
    public boolean equals(Object other) {
        return other instanceof JavaClass &&
            this.getValue() == ((JavaClass)other).getValue();
    }
    
    private void initializeInterface(Class<?> javaClass) {
        Map<String, AssignedName> staticNames  = new HashMap<String, AssignedName>(STATIC_RESERVED_NAMES);
        List<ConstantField> constantFields = new ArrayList<ConstantField>(); 
        Map<String, NamedInstaller> staticCallbacks = new HashMap<String, NamedInstaller>();
        Field[] fields = EMPTY_FIELD_ARRAY;
        try {
            fields = javaClass.getDeclaredFields();
        } catch (SecurityException e) {
            try {
                fields = javaClass.getFields();
            } catch (SecurityException e2) {
            }
        }
        for (int i = fields.length; --i >= 0; ) {
            Field field = fields[i];
            if (javaClass != field.getDeclaringClass()) continue;
            if (ConstantField.isConstant(field)) {
                constantFields.add(new ConstantField(field));
            }
            
            String name = field.getName();
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                AssignedName assignedName = staticNames.get(name);
                if (assignedName != null && assignedName.type < AssignedName.FIELD) continue;
                staticNames.put(name,new AssignedName(name,AssignedName.FIELD));
                staticCallbacks.put(name,new StaticFieldGetterInstaller(name,field));
                if (!Modifier.isFinal(modifiers)) {
                    String setName = name + '=';
                    staticCallbacks.put(setName,new StaticFieldSetterInstaller(setName,field));
                }
            }            
        }
        this.staticAssignedNames = staticNames;
        this.staticInstallers = staticCallbacks;        
        this.constantFields = constantFields;
    }

    private void initializeClass(Class<?> javaClass) {
        Class<?> superclass = javaClass.getSuperclass();
        Map<String, AssignedName> staticNames;
        Map<String, AssignedName> instanceNames;
        if (superclass == null) {
            staticNames = new HashMap<String, AssignedName>();
            instanceNames = new HashMap<String, AssignedName>();
        } else {
            JavaClass superJavaClass = get(getRuntime(),superclass);
            staticNames = new HashMap<String, AssignedName>(superJavaClass.getStaticAssignedNames());
            instanceNames = new HashMap<String, AssignedName>(superJavaClass.getInstanceAssignedNames());
        }
        staticNames.putAll(STATIC_RESERVED_NAMES);
        instanceNames.putAll(INSTANCE_RESERVED_NAMES);
        Map<String, NamedInstaller> staticCallbacks = new HashMap<String, NamedInstaller>();
        Map<String, NamedInstaller> instanceCallbacks = new HashMap<String, NamedInstaller>();
        List<ConstantField> constantFields = new ArrayList<ConstantField>(); 
        Field[] fields = EMPTY_FIELD_ARRAY;
        try {
            fields = javaClass.getFields();
        } catch (SecurityException e) {
        }
        for (int i = fields.length; --i >= 0; ) {
            Field field = fields[i];
            if (javaClass != field.getDeclaringClass()) continue;

            if (ConstantField.isConstant(field)) {
                constantFields.add(new ConstantField(field));
                continue;
            }
            String name = field.getName();
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                AssignedName assignedName = staticNames.get(name);
                if (assignedName != null && assignedName.type < AssignedName.FIELD)
                    continue;
                staticNames.put(name,new AssignedName(name,AssignedName.FIELD));
                staticCallbacks.put(name,new StaticFieldGetterInstaller(name,field));
                if (!Modifier.isFinal(modifiers)) {
                    String setName = name + '=';
                    staticCallbacks.put(setName,new StaticFieldSetterInstaller(setName,field));
                }
            } else {
                AssignedName assignedName = instanceNames.get(name);
                if (assignedName != null && assignedName.type < AssignedName.FIELD)
                    continue;
                instanceNames.put(name, new AssignedName(name,AssignedName.FIELD));
                instanceCallbacks.put(name, new InstanceFieldGetterInstaller(name,field));
                if (!Modifier.isFinal(modifiers)) {
                    String setName = name + '=';
                    instanceCallbacks.put(setName, new InstanceFieldSetterInstaller(setName,field));
                }
            }
        }
        // TODO: protected methods.  this is going to require a rework 
        // of some of the mechanism.  
        Method[] methods = EMPTY_METHOD_ARRAY;
        for (Class c = javaClass; c != null; c = c.getSuperclass()) {
            try {
                methods = javaClass.getMethods();
                break;
            } catch (SecurityException e) {
            }
        }
        for (int i = methods.length; --i >= 0; ) {
            // we need to collect all methods, though we'll only
            // install the ones that are named in this class
            Method method = methods[i];
            String name = method.getName();
            if (Modifier.isStatic(method.getModifiers())) {
                AssignedName assignedName = staticNames.get(name);
                if (assignedName == null) {
                    staticNames.put(name,new AssignedName(name,AssignedName.METHOD));
                } else {
                    if (assignedName.type < AssignedName.METHOD)
                        continue;
                    if (assignedName.type != AssignedName.METHOD) {
                        staticCallbacks.remove(name);
                        staticCallbacks.remove(name+'=');
                        staticNames.put(name,new AssignedName(name,AssignedName.METHOD));
                    }
                }
                StaticMethodInvokerInstaller invoker = (StaticMethodInvokerInstaller)staticCallbacks.get(name);
                if (invoker == null) {
                    invoker = new StaticMethodInvokerInstaller(name);
                    staticCallbacks.put(name,invoker);
                }
                invoker.addMethod(method,javaClass);
            } else {
                AssignedName assignedName = instanceNames.get(name);
                if (assignedName == null) {
                    instanceNames.put(name,new AssignedName(name,AssignedName.METHOD));
                } else {
                    if (assignedName.type < AssignedName.METHOD)
                        continue;
                    if (assignedName.type != AssignedName.METHOD) {
                        instanceCallbacks.remove(name);
                        instanceCallbacks.remove(name+'=');
                        instanceNames.put(name,new AssignedName(name,AssignedName.METHOD));
                    }
                }
                InstanceMethodInvokerInstaller invoker = (InstanceMethodInvokerInstaller)instanceCallbacks.get(name);
                if (invoker == null) {
                    invoker = new InstanceMethodInvokerInstaller(name);
                    instanceCallbacks.put(name,invoker);
                }
                invoker.addMethod(method,javaClass);
            }
        }
        // TODO: protected methods.  this is going to require a rework 
        // of some of the mechanism.  
        Constructor[] constructors = EMPTY_CONSTRUCTOR_ARRAY;
        try {
            constructors = javaClass.getConstructors();
        } catch (SecurityException e) {
        }
        for (int i = constructors.length; --i >= 0; ) {
            // we need to collect all methods, though we'll only
            // install the ones that are named in this class
            Constructor ctor = constructors[i];
            
            if (constructorInstaller == null) {
                constructorInstaller = new ConstructorInvokerInstaller("__jcreate!");
            }
            constructorInstaller.addConstructor(ctor,javaClass);
        }
        
        this.staticAssignedNames = staticNames;
        this.instanceAssignedNames = instanceNames;
        this.staticInstallers = staticCallbacks;
        this.instanceInstallers = instanceCallbacks;
        this.constantFields = constantFields;
    }
    
    public void setupProxy(final RubyClass proxy) {
        assert proxyLock.isHeldByCurrentThread();
        proxy.defineFastMethod("__jsend!", __jsend_method);
        final Class<?> javaClass = javaClass();
        if (javaClass.isInterface()) {
            setupInterfaceProxy(proxy);
            return;
        }
        assert this.proxyClass == null;
        this.unfinishedProxyClass = proxy;
        if (javaClass.isArray() || javaClass.isPrimitive()) {
            // see note below re: 2-field kludge
            this.proxyClass = proxy;
            this.proxyModule = proxy;
            return;
        }

        for (ConstantField field: constantFields) {
            field.install(proxy);
        }
        for (Iterator<NamedInstaller> iter = staticInstallers.values().iterator(); iter.hasNext(); ) {
            NamedInstaller installer = iter.next();
            if (installer.type == NamedInstaller.STATIC_METHOD && installer.hasLocalMethod()) {
                assignAliases((MethodInstaller)installer,staticAssignedNames);
            }
            installer.install(proxy);
        }
        for (Iterator<NamedInstaller> iter = instanceInstallers.values().iterator(); iter.hasNext(); ) {
            NamedInstaller installer = iter.next();
            if (installer.type == NamedInstaller.INSTANCE_METHOD && installer.hasLocalMethod()) {
                assignAliases((MethodInstaller)installer,instanceAssignedNames);
            }
            installer.install(proxy);
        }
        
        if (constructorInstaller != null) {
            constructorInstaller.install(proxy);
        }
        
        // setup constants for public inner classes
        Class<?>[] classes = EMPTY_CLASS_ARRAY;
        try {
            classes = javaClass.getClasses();
        } catch (SecurityException e) {
        }
        for (int i = classes.length; --i >= 0; ) {
            if (javaClass == classes[i].getDeclaringClass()) {
                Class<?> clazz = classes[i];
                String simpleName = getSimpleName(clazz);
                
                if (simpleName.length() == 0) continue;
                
                // Ignore bad constant named inner classes pending JRUBY-697
                if (IdUtil.isConstant(simpleName) && proxy.getConstantAt(simpleName) == null) {
                    proxy.setConstant(simpleName,
                        Java.get_proxy_class(JAVA_UTILITIES,get(getRuntime(),clazz)));
                }
            }
        }
        // FIXME: bit of a kludge here (non-interface classes assigned to both
        // class and module fields). simplifies proxy extender code, will go away
        // when JI is overhauled (and proxy extenders are deprecated).
        this.proxyClass = proxy;
        this.proxyModule = proxy;

        applyProxyExtenders();

        // TODO: we can probably release our references to the constantFields
        // array and static/instance callback hashes at this point. 
    }

    private static void assignAliases(MethodInstaller installer, Map<String, AssignedName> assignedNames) {
        String name = installer.name;
        String rubyCasedName = JavaUtil.getRubyCasedName(name);
        addUnassignedAlias(rubyCasedName,assignedNames,installer);

        String javaPropertyName = JavaUtil.getJavaPropertyName(name);
        String rubyPropertyName = null;

        for (Method method: installer.methods) {
            Class<?>[] argTypes = method.getParameterTypes();
            Class<?> resultType = method.getReturnType();
            int argCount = argTypes.length;

            // Add property name aliases
            if (javaPropertyName != null) {
                if (rubyCasedName.startsWith("get_")) {
                    rubyPropertyName = rubyCasedName.substring(4);
                    if (argCount == 0 ||                                // getFoo      => foo
                        argCount == 1 && argTypes[0] == int.class) {    // getFoo(int) => foo(int)

                        addUnassignedAlias(javaPropertyName,assignedNames,installer);
                        addUnassignedAlias(rubyPropertyName,assignedNames,installer);
                    }
                } else if (rubyCasedName.startsWith("set_")) {
                    rubyPropertyName = rubyCasedName.substring(4);
                    if (argCount == 1 && resultType == void.class) {    // setFoo(Foo) => foo=(Foo)
                        addUnassignedAlias(javaPropertyName+'=',assignedNames,installer);
                        addUnassignedAlias(rubyPropertyName+'=',assignedNames,installer);
                    }
                } else if (rubyCasedName.startsWith("is_")) {
                    rubyPropertyName = rubyCasedName.substring(3);
                    if (resultType == boolean.class) {                  // isFoo() => foo, isFoo(*) => foo(*)
                        addUnassignedAlias(javaPropertyName,assignedNames,installer);
                        addUnassignedAlias(rubyPropertyName,assignedNames,installer);
                    }
                }
            }

            // Additionally add ?-postfixed aliases to any boolean methods and properties.
            if (resultType == boolean.class) {
                // is_something?, contains_thing?
                addUnassignedAlias(rubyCasedName+'?',assignedNames,installer);
                if (rubyPropertyName != null) {
                    // something?
                    addUnassignedAlias(rubyPropertyName+'?',assignedNames,installer);
                }
            }
        }
    }
    
    private static void addUnassignedAlias(String name, Map<String, AssignedName> assignedNames,
            MethodInstaller installer) {
        if (name != null) {
            AssignedName assignedName = (AssignedName)assignedNames.get(name);
            if (assignedName == null) {
                installer.addAlias(name);
                assignedNames.put(name,new AssignedName(name,AssignedName.ALIAS));
            } else if (assignedName.type == AssignedName.ALIAS) {
                installer.addAlias(name);
            } else if (assignedName.type > AssignedName.ALIAS) {
                // TODO: there will be some additional logic in this branch
                // dealing with conflicting protected fields. 
                installer.addAlias(name);
                assignedNames.put(name,new AssignedName(name,AssignedName.ALIAS));
            }
        }
    }
    
    // old (quasi-deprecated) interface class
    private void setupInterfaceProxy(final RubyClass proxy) {
        assert javaClass().isInterface();
        assert proxyLock.isHeldByCurrentThread();
        assert this.proxyClass == null;
        this.proxyClass = proxy;
        // nothing else to here - the module version will be
        // included in the class.
    }
    
    public void setupInterfaceModule(final RubyModule module) {
        assert javaClass().isInterface();
        assert proxyLock.isHeldByCurrentThread();
        assert this.proxyModule == null;
        this.unfinishedProxyModule = module;
        Class<?> javaClass = javaClass();
        for (ConstantField field: constantFields) {
            field.install(module);
        }
        for (Iterator<NamedInstaller> iter = staticInstallers.values().iterator(); iter.hasNext(); ) {
            NamedInstaller installer = iter.next();
            if (installer.type == NamedInstaller.STATIC_METHOD && installer.hasLocalMethod()) {
                assignAliases((MethodInstaller)installer,staticAssignedNames);
            }
            installer.install(module);
        }        
        // setup constants for public inner classes
        Class<?>[] classes = EMPTY_CLASS_ARRAY;
        try {
            classes = javaClass.getClasses();
        } catch (SecurityException e) {
        }
        for (int i = classes.length; --i >= 0; ) {
            if (javaClass == classes[i].getDeclaringClass()) {
                Class<?> clazz = classes[i];
                String simpleName = getSimpleName(clazz);
                if (simpleName.length() == 0) continue;
                
                // Ignore bad constant named inner classes pending JRUBY-697
                if (IdUtil.isConstant(simpleName) && module.getConstantAt(simpleName) == null) {
                    module.const_set(getRuntime().newString(simpleName),
                        Java.get_proxy_class(JAVA_UTILITIES,get(getRuntime(),clazz)));
                }
            }
        }
        
        this.proxyModule = module;
        applyProxyExtenders();
    }

    public void addProxyExtender(final IRubyObject extender) {
        lockProxy();
        try {
            if (!extender.respondsTo("extend_proxy")) {
                throw getRuntime().newTypeError("proxy extender must have an extend_proxy method");
            }
            if (proxyModule == null) {
                if (proxyExtenders == null) {
                    proxyExtenders = new ArrayList<IRubyObject>();
                }
                proxyExtenders.add(extender);
            } else {
                getRuntime().getWarnings().warn(ID.PROXY_EXTENDED_LATE, " proxy extender added after proxy class created for " + this);
                extendProxy(extender);
            }
        } finally {
            unlockProxy();
        }
    }
    
    private void applyProxyExtenders() {
        ArrayList<IRubyObject> extenders;
        if ((extenders = proxyExtenders) != null) {
            for (IRubyObject extender : extenders) {
                extendProxy(extender);
            }
            proxyExtenders = null;
        }
    }

    private void extendProxy(IRubyObject extender) {
        extender.callMethod(getRuntime().getCurrentContext(), "extend_proxy", proxyModule);
    }
    
    @JRubyMethod(required = 1)
    public IRubyObject extend_proxy(IRubyObject extender) {
        addProxyExtender(extender);
        return getRuntime().getNil();
    }
    
    public static JavaClass get(Ruby runtime, Class<?> klass) {
        JavaClass javaClass = runtime.getJavaSupport().getJavaClassFromCache(klass);
        if (javaClass == null) {
            javaClass = createJavaClass(runtime,klass);
        }
        return javaClass;
    }
    
    public static RubyArray getRubyArray(Ruby runtime, Class<?>[] classes) {
        IRubyObject[] javaClasses = new IRubyObject[classes.length];
        for (int i = classes.length; --i >= 0; ) {
            javaClasses[i] = get(runtime, classes[i]);
        }
        return runtime.newArrayNoCopy(javaClasses);
    }

    private static synchronized JavaClass createJavaClass(Ruby runtime, Class<?> klass) {
        // double-check the cache now that we're synchronized
        JavaClass javaClass = runtime.getJavaSupport().getJavaClassFromCache(klass);
        if (javaClass == null) {
            javaClass = new JavaClass(runtime, klass);
            runtime.getJavaSupport().putJavaClassIntoCache(javaClass);
        }
        return javaClass;
    }

    public static RubyClass createJavaClassClass(Ruby runtime, RubyModule javaModule) {
        // FIXME: Determine if a real allocator is needed here. Do people want to extend
        // JavaClass? Do we want them to do that? Can you Class.new(JavaClass)? Should
        // you be able to?
        // TODO: NOT_ALLOCATABLE_ALLOCATOR is probably ok here, since we don't intend for people to monkey with
        // this type and it can't be marshalled. Confirm. JRUBY-415
        RubyClass result = javaModule.defineClassUnder("JavaClass", javaModule.fastGetClass("JavaObject"), ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR); 
        
        result.includeModule(runtime.fastGetModule("Comparable"));
        
        result.defineAnnotatedMethods(JavaClass.class);

        result.getMetaClass().undefineMethod("new");
        result.getMetaClass().undefineMethod("allocate");

        return result;
    }
    
    public static synchronized JavaClass forNameVerbose(Ruby runtime, String className) {
        Class<?> klass = runtime.getJavaSupport().loadJavaClassVerbose(className);
        return JavaClass.get(runtime, klass);
    }
    
    public static synchronized JavaClass forNameQuiet(Ruby runtime, String className) {
        Class klass = runtime.getJavaSupport().loadJavaClassQuiet(className);
        return JavaClass.get(runtime, klass);
    }

    @JRubyMethod(name = "for_name", required = 1, meta = true)
    public static JavaClass for_name(IRubyObject recv, IRubyObject name) {
        return forNameVerbose(recv.getRuntime(), name.asJavaString());
    }
    
    private static final Callback __jsend_method = new Callback() {
            public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
                String name = args[0].asJavaString();
                
                DynamicMethod method = self.getMetaClass().searchMethod(name);
                int v = method.getArity().getValue();
                
                IRubyObject[] newArgs = new IRubyObject[args.length - 1];
                System.arraycopy(args, 1, newArgs, 0, newArgs.length);

                if(v < 0 || v == (newArgs.length)) {
                    return RuntimeHelpers.invoke(self.getRuntime().getCurrentContext(), self, name, newArgs, block);
                } else {
                    RubyClass superClass = self.getMetaClass().getSuperClass();
                    return RuntimeHelpers.invokeAs(self.getRuntime().getCurrentContext(), superClass, self, name, newArgs, block);
                }
            }

            public Arity getArity() {
                return Arity.optional();
            }
        };

    @JRubyMethod
    public RubyModule ruby_class() {
        // Java.getProxyClass deals with sync issues, so we won't duplicate the logic here
        return Java.getProxyClass(getRuntime(), this);
    }

    @JRubyMethod(name = "public?")
    public RubyBoolean public_p() {
        return getRuntime().newBoolean(Modifier.isPublic(javaClass().getModifiers()));
    }

    @JRubyMethod(name = "protected?")
    public RubyBoolean protected_p() {
        return getRuntime().newBoolean(Modifier.isProtected(javaClass().getModifiers()));
    }

    @JRubyMethod(name = "private?")
    public RubyBoolean private_p() {
        return getRuntime().newBoolean(Modifier.isPrivate(javaClass().getModifiers()));
    }

    public Class javaClass() {
        return (Class) getValue();
    }

    @JRubyMethod(name = "final?")
    public RubyBoolean final_p() {
        return getRuntime().newBoolean(Modifier.isFinal(javaClass().getModifiers()));
    }

    @JRubyMethod(name = "interface?")
    public RubyBoolean interface_p() {
        return getRuntime().newBoolean(javaClass().isInterface());
    }

    @JRubyMethod(name = "array?")
    public RubyBoolean array_p() {
        return getRuntime().newBoolean(javaClass().isArray());
    }
    
    @JRubyMethod(name = "enum?")
    public RubyBoolean enum_p() {
        return getRuntime().newBoolean(javaClass().isEnum());
    }
    
    @JRubyMethod(name = "annotation?")
    public RubyBoolean annotation_p() {
        return getRuntime().newBoolean(javaClass().isAnnotation());
    }
    
    @JRubyMethod(name = "anonymous_class?")
    public RubyBoolean anonymous_class_p() {
        return getRuntime().newBoolean(javaClass().isAnonymousClass());
    }
    
    @JRubyMethod(name = "local_class?")
    public RubyBoolean local_class_p() {
        return getRuntime().newBoolean(javaClass().isLocalClass());
    }
    
    @JRubyMethod(name = "member_class?")
    public RubyBoolean member_class_p() {
        return getRuntime().newBoolean(javaClass().isMemberClass());
    }
    
    @JRubyMethod(name = "synthetic?")
    public IRubyObject synthetic_p() {
        return getRuntime().newBoolean(javaClass().isSynthetic());
    }

    @JRubyMethod(name = {"name", "to_s"})
    public RubyString name() {
        return getRuntime().newString(javaClass().getName());
    }

    @JRubyMethod
    public IRubyObject canonical_name() {
        String canonicalName = javaClass().getCanonicalName();
        if (canonicalName != null) {
            return getRuntime().newString(canonicalName);
        }
        return getRuntime().getNil();
    }
    
    @JRubyMethod(name = "package")
    public IRubyObject get_package() {
        return Java.getInstance(getRuntime(), javaClass().getPackage());
    }

    @JRubyMethod
    public IRubyObject class_loader() {
        return Java.getInstance(getRuntime(), javaClass().getClassLoader());
    }

    @JRubyMethod
    public IRubyObject protection_domain() {
        return Java.getInstance(getRuntime(), javaClass().getProtectionDomain());
    }
    
    @JRubyMethod(required = 1)
    public IRubyObject resource(IRubyObject name) {
        return Java.getInstance(getRuntime(), javaClass().getResource(name.asJavaString()));
    }

    @JRubyMethod(required = 1)
    public IRubyObject resource_as_stream(IRubyObject name) {
        return Java.getInstance(getRuntime(), javaClass().getResourceAsStream(name.asJavaString()));
    }
    
    @JRubyMethod(required = 1)
    public IRubyObject resource_as_string(IRubyObject name) {
        InputStream in = javaClass().getResourceAsStream(name.asJavaString());
        if (in == null) return getRuntime().getNil();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            int len;
            byte[] buf = new byte[4096];
            while ((len = in.read(buf)) >= 0) {
                out.write(buf, 0, len);
            }
        } catch (IOException e) {
            throw getRuntime().newIOErrorFromException(e);
        }
        return getRuntime().newString(new ByteList(out.toByteArray(), false));
    }
    
    @SuppressWarnings("unchecked")
    @JRubyMethod(required = 1)
    public IRubyObject annotation(IRubyObject annoClass) {
        if (!(annoClass instanceof JavaClass)) {
            throw getRuntime().newTypeError(annoClass, getRuntime().getJavaSupport().getJavaClassClass());
        }
        return Java.getInstance(getRuntime(), javaClass().getAnnotation(((JavaClass)annoClass).javaClass()));
    }
    
    @JRubyMethod
    public IRubyObject annotations() {
        // note: intentionally returning the actual array returned from Java, rather
        // than wrapping it in a RubyArray. wave of the future, when java_class will
        // return the actual class, rather than a JavaClass wrapper.
        return Java.getInstance(getRuntime(), javaClass().getAnnotations());
    }
    
    @JRubyMethod(name = "annotations?")
    public RubyBoolean annotations_p() {
        return getRuntime().newBoolean(javaClass().getAnnotations().length > 0);
    }
    
    @JRubyMethod
    public IRubyObject declared_annotations() {
        // see note above re: return type
        return Java.getInstance(getRuntime(), javaClass().getDeclaredAnnotations());
    }
    
    @JRubyMethod(name = "declared_annotations?")
    public RubyBoolean declared_annotations_p() {
        return getRuntime().newBoolean(javaClass().getDeclaredAnnotations().length > 0);
    }
    
    @SuppressWarnings("unchecked")
    @JRubyMethod(name = "annotation_present?", required = 1)
    public IRubyObject annotation_present_p(IRubyObject annoClass) {
        if (!(annoClass instanceof JavaClass)) {
            throw getRuntime().newTypeError(annoClass, getRuntime().getJavaSupport().getJavaClassClass());
        }
        return getRuntime().newBoolean(javaClass().isAnnotationPresent(((JavaClass)annoClass).javaClass()));
    }
    
    @JRubyMethod
    public IRubyObject modifiers() {
        return getRuntime().newFixnum(javaClass().getModifiers());
    }

    @JRubyMethod
    public IRubyObject declaring_class() {
        Class<?> clazz = javaClass().getDeclaringClass();
        if (clazz != null) {
            return JavaClass.get(getRuntime(), clazz);
        }
        return getRuntime().getNil();
    }

    @JRubyMethod
    public IRubyObject enclosing_class() {
        return Java.getInstance(getRuntime(), javaClass().getEnclosingClass());
    }
    
    @JRubyMethod
    public IRubyObject enclosing_constructor() {
        Constructor<?> ctor = javaClass().getEnclosingConstructor();
        if (ctor != null) {
            return new JavaConstructor(getRuntime(), ctor);
        }
        return getRuntime().getNil();
    }

    @JRubyMethod
    public IRubyObject enclosing_method() {
        Method meth = javaClass().getEnclosingMethod();
        if (meth != null) {
            return new JavaMethod(getRuntime(), meth);
        }
        return getRuntime().getNil();
    }

    @JRubyMethod
    public IRubyObject enum_constants() {
        return Java.getInstance(getRuntime(), javaClass().getEnumConstants());
    }

    @JRubyMethod
    public IRubyObject generic_interfaces() {
        return Java.getInstance(getRuntime(), javaClass().getGenericInterfaces());
    }
    
    @JRubyMethod
    public IRubyObject generic_superclass() {
        return Java.getInstance(getRuntime(), javaClass().getGenericSuperclass());
    }
    
    @JRubyMethod
    public IRubyObject type_parameters() {
        return Java.getInstance(getRuntime(), javaClass().getTypeParameters());
    }
    
    @JRubyMethod
    public IRubyObject signers() {
        return Java.getInstance(getRuntime(), javaClass().getSigners());
    }
    
    private static String getSimpleName(Class<?> clazz) {
 		if (clazz.isArray()) {
 			return getSimpleName(clazz.getComponentType()) + "[]";
 		}
 
 		String className = clazz.getName();
 		int len = className.length();
        int i = className.lastIndexOf('$');
 		if (i != -1) {
            do {
 				i++;
 			} while (i < len && Character.isDigit(className.charAt(i)));
 			return className.substring(i);
 		}
 
 		return className.substring(className.lastIndexOf('.') + 1);
 	}

    @JRubyMethod
    public RubyString simple_name() {
        return getRuntime().newString(getSimpleName(javaClass()));
    }

    @JRubyMethod
    public IRubyObject superclass() {
        Class<?> superclass = javaClass().getSuperclass();
        if (superclass == null) {
            return getRuntime().getNil();
        }
        return JavaClass.get(getRuntime(), superclass);
    }

    @JRubyMethod(name = "<=>", required = 1)
    public RubyFixnum op_cmp(IRubyObject other) {
        if (! (other instanceof JavaClass)) {
            throw getRuntime().newTypeError("<=> requires JavaClass (" + other.getType() + " given)");
        }
        JavaClass otherClass = (JavaClass) other;
        if (this.javaClass() == otherClass.javaClass()) {
            return getRuntime().newFixnum(0);
        }
        if (otherClass.javaClass().isAssignableFrom(this.javaClass())) {
            return getRuntime().newFixnum(-1);
        }
        return getRuntime().newFixnum(1);
    }

    @JRubyMethod
    public RubyArray java_instance_methods() {
        return java_methods(javaClass().getMethods(), false);
    }

    @JRubyMethod
    public RubyArray declared_instance_methods() {
        return java_methods(javaClass().getDeclaredMethods(), false);
    }

    private RubyArray java_methods(Method[] methods, boolean isStatic) {
        RubyArray result = getRuntime().newArray(methods.length);
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (isStatic == Modifier.isStatic(method.getModifiers())) {
                result.append(JavaMethod.create(getRuntime(), method));
            }
        }
        return result;
    }

    @JRubyMethod
    public RubyArray java_class_methods() {
        return java_methods(javaClass().getMethods(), true);
    }

    @JRubyMethod
    public RubyArray declared_class_methods() {
        return java_methods(javaClass().getDeclaredMethods(), true);
    }

    @JRubyMethod(required = 1, rest = true)
    public JavaMethod java_method(IRubyObject[] args) throws ClassNotFoundException {
        String methodName = args[0].asJavaString();
        Class<?>[] argumentTypes = buildArgumentTypes(args);
        return JavaMethod.create(getRuntime(), javaClass(), methodName, argumentTypes);
    }

    @JRubyMethod(required = 1, rest = true)
    public JavaMethod declared_method(IRubyObject[] args) throws ClassNotFoundException {
        String methodName = args[0].asJavaString();
        Class<?>[] argumentTypes = buildArgumentTypes(args);
        return JavaMethod.createDeclared(getRuntime(), javaClass(), methodName, argumentTypes);
    }

    @JRubyMethod(required = 1, rest = true)
    public JavaCallable declared_method_smart(IRubyObject[] args) throws ClassNotFoundException {
        String methodName = args[0].asJavaString();
        Class<?>[] argumentTypes = buildArgumentTypes(args);
 
        JavaCallable callable = getMatchingCallable(getRuntime(), javaClass(), methodName, argumentTypes);

        if (callable != null) return callable;

        throw getRuntime().newNameError("undefined method '" + methodName + "' for class '" + javaClass().getName() + "'",
                methodName);
    }
    
    public static JavaCallable getMatchingCallable(Ruby runtime, Class<?> javaClass, String methodName, Class<?>[] argumentTypes) {
        if ("<init>".equals(methodName)) {
            return JavaConstructor.getMatchingConstructor(runtime, javaClass, argumentTypes);
        } else {
            // FIXME: do we really want 'declared' methods?  includes private/protected, and does _not_
            // include superclass methods
            return JavaMethod.getMatchingDeclaredMethod(runtime, javaClass, methodName, argumentTypes);
        }
    }

    private Class<?>[] buildArgumentTypes(IRubyObject[] args) throws ClassNotFoundException {
        if (args.length < 1) {
            throw getRuntime().newArgumentError(args.length, 1);
        }
        Class<?>[] argumentTypes = new Class[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            JavaClass type;
            if (args[i] instanceof JavaClass) {
                type = (JavaClass)args[i];
            } else if (args[i].respondsTo("java_class")) {
                type = (JavaClass)args[i].callMethod(getRuntime().getCurrentContext(), "java_class");
            } else {
                type = for_name(this, args[i]);
            }
            argumentTypes[i - 1] = type.javaClass();
        }
        return argumentTypes;
    }

    @JRubyMethod
    public RubyArray constructors() {
        RubyArray ctors;
        if ((ctors = constructors) != null) return ctors;
        return constructors = buildConstructors(javaClass().getConstructors());
    }
    
    @JRubyMethod
    public RubyArray classes() {
        return JavaClass.getRubyArray(getRuntime(), javaClass().getClasses());
    }

    @JRubyMethod
    public RubyArray declared_classes() {
        Ruby runtime = getRuntime();
        RubyArray result = runtime.newArray();
        Class<?> javaClass = javaClass();
        try {
            Class<?>[] classes = javaClass.getDeclaredClasses();
            for (int i = 0; i < classes.length; i++) {
                if (Modifier.isPublic(classes[i].getModifiers())) {
                    result.append(get(runtime, classes[i]));
                }
            }
        } catch (SecurityException e) {
            // restrictive security policy; no matter, we only want public
            // classes anyway
            try {
                Class<?>[] classes = javaClass.getClasses();
                for (int i = 0; i < classes.length; i++) {
                    if (javaClass == classes[i].getDeclaringClass()) {
                        result.append(get(runtime, classes[i]));
                    }
                }
            } catch (SecurityException e2) {
                // very restrictive policy (disallows Member.PUBLIC)
                // we'd never actually get this far in that case
            }
        }
        return result;
    }

    @JRubyMethod
    public RubyArray declared_constructors() {
        return buildConstructors(javaClass().getDeclaredConstructors());
    }

    private RubyArray buildConstructors(Constructor<?>[] constructors) {
        RubyArray result = getRuntime().newArray(constructors.length);
        for (int i = 0; i < constructors.length; i++) {
            result.append(new JavaConstructor(getRuntime(), constructors[i]));
        }
        return result;
    }

    @JRubyMethod(rest = true)
    public JavaConstructor constructor(IRubyObject[] args) {
        try {
            Class<?>[] parameterTypes = buildClassArgs(args);
            Constructor<?> constructor = javaClass().getConstructor(parameterTypes);
            return new JavaConstructor(getRuntime(), constructor);
        } catch (NoSuchMethodException nsme) {
            throw getRuntime().newNameError("no matching java constructor", null);
        }
    }

    @JRubyMethod(rest = true)
    public JavaConstructor declared_constructor(IRubyObject[] args) {
        try {
            Class<?>[] parameterTypes = buildClassArgs(args);
            Constructor<?> constructor = javaClass().getDeclaredConstructor (parameterTypes);
            return new JavaConstructor(getRuntime(), constructor);
        } catch (NoSuchMethodException nsme) {
            throw getRuntime().newNameError("no matching java constructor", null);
        }
    }

    private Class<?>[] buildClassArgs(IRubyObject[] args) {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            JavaClass type;
            if (args[i] instanceof JavaClass) {
                type = (JavaClass)args[i];
            } else if (args[i].respondsTo("java_class")) {
                type = (JavaClass)args[i].callMethod(getRuntime().getCurrentContext(), "java_class");
            } else {
                type = for_name(this, args[i]);
            }
            parameterTypes[i] = type.javaClass();
        }
        return parameterTypes;
    }

    @JRubyMethod
    public JavaClass array_class() {
        return JavaClass.get(getRuntime(), Array.newInstance(javaClass(), 0).getClass());
    }
   
    @JRubyMethod(required = 1)
    public JavaObject new_array(IRubyObject lengthArgument) {
        if (lengthArgument instanceof RubyInteger) {
            // one-dimensional array
            int length = (int) ((RubyInteger) lengthArgument).getLongValue();
            return new JavaArray(getRuntime(), Array.newInstance(javaClass(), length));
        } else if (lengthArgument instanceof RubyArray) {
            // n-dimensional array
            List list = ((RubyArray)lengthArgument).getList();
            int length = list.size();
            if (length == 0) {
                throw getRuntime().newArgumentError("empty dimensions specifier for java array");
            }
            int[] dimensions = new int[length];
            for (int i = length; --i >= 0; ) {
                IRubyObject dimensionLength = (IRubyObject)list.get(i);
                if ( !(dimensionLength instanceof RubyInteger) ) {
                    throw getRuntime()
                    .newTypeError(dimensionLength, getRuntime().getInteger());
                }
                dimensions[i] = (int) ((RubyInteger) dimensionLength).getLongValue();
            }
            return new JavaArray(getRuntime(), Array.newInstance(javaClass(), dimensions));
        } else {
            throw getRuntime().newArgumentError(
                    "invalid length or dimensions specifier for java array" +
            " - must be Integer or Array of Integer");
        }
    }
   
    public IRubyObject emptyJavaArray(ThreadContext context) {
        JavaArray javaArray = new JavaArray(getRuntime(), Array.newInstance(javaClass(), 0));
        RubyClass proxyClass = (RubyClass)Java.get_proxy_class(javaArray, array_class());
        
        ArrayJavaProxy proxy = new ArrayJavaProxy(context.getRuntime(), proxyClass);
        proxy.dataWrapStruct(javaArray);
        
        return proxy;
    }
   
    public IRubyObject javaArraySubarray(ThreadContext context, JavaArray fromArray, int index, int size) {
        int actualLength = Array.getLength(fromArray.getValue());
        if (index >= actualLength) {
            return context.getRuntime().getNil();
        } else {
            if (index + size > actualLength) {
                size = actualLength - index;
            }
            
            Object newArray = Array.newInstance(javaClass(), size);
            JavaArray javaArray = new JavaArray(getRuntime(), newArray);
            System.arraycopy(fromArray.getValue(), index, newArray, 0, size);
            RubyClass proxyClass = (RubyClass)Java.get_proxy_class(javaArray, array_class());

            ArrayJavaProxy proxy = new ArrayJavaProxy(context.getRuntime(), proxyClass);
            proxy.dataWrapStruct(javaArray);

            return proxy;
        }
    }
   
    /**
     * Contatenate two Java arrays into a new one. The component type of the
     * additional array must be assignable to the component type of the
     * original array.
     * 
     * @param context
     * @param original
     * @param additional
     * @return
     */
    public IRubyObject concatArrays(ThreadContext context, JavaArray original, JavaArray additional) {
        int oldLength = (int)original.length().getLongValue();
        int addLength = (int)additional.length().getLongValue();
        Object newArray = Array.newInstance(javaClass(), oldLength + addLength);
        JavaArray javaArray = new JavaArray(getRuntime(), newArray);
        System.arraycopy(original.getValue(), 0, newArray, 0, oldLength);
        System.arraycopy(additional.getValue(), 0, newArray, oldLength, addLength);
        RubyClass proxyClass = (RubyClass)Java.get_proxy_class(javaArray, array_class());

        ArrayJavaProxy proxy = new ArrayJavaProxy(context.getRuntime(), proxyClass);
        proxy.dataWrapStruct(javaArray);

        return proxy;
    }
   
    /**
     * The slow version for when concatenating a Java array of a different type.
     * 
     * @param context
     * @param original
     * @param additional
     * @return
     */
    public IRubyObject concatArrays(ThreadContext context, JavaArray original, IRubyObject additional) {
        int oldLength = (int)original.length().getLongValue();
        int addLength = (int)((RubyFixnum)RuntimeHelpers.invoke(context, additional, "length")).getLongValue();
        Object newArray = Array.newInstance(javaClass(), oldLength + addLength);
        JavaArray javaArray = new JavaArray(getRuntime(), newArray);
        System.arraycopy(original.getValue(), 0, newArray, 0, oldLength);
        RubyClass proxyClass = (RubyClass)Java.get_proxy_class(javaArray, array_class());
        ArrayJavaProxy proxy = new ArrayJavaProxy(context.getRuntime(), proxyClass);
        proxy.dataWrapStruct(javaArray);
        
        Ruby runtime = context.getRuntime();
        for (int i = 0; i < addLength; i++) {
            RuntimeHelpers.invoke(context, proxy, "[]=", runtime.newFixnum(oldLength + i), 
                    RuntimeHelpers.invoke(context, additional, "[]", runtime.newFixnum(i)));
        }

        return proxy;
    }

    public IRubyObject javaArrayFromRubyArray(ThreadContext context, IRubyObject fromArray) {
        Ruby runtime = context.getRuntime();
        if (!(fromArray instanceof RubyArray)) {
            throw runtime.newTypeError(fromArray, runtime.getArray());
        }
        RubyArray rubyArray = (RubyArray)fromArray;
        JavaArray javaArray = new JavaArray(getRuntime(), Array.newInstance(javaClass(), rubyArray.size()));
        
        if (javaClass().isArray()) {
            // if it's an array of arrays, recurse with the component type
            for (int i = 0; i < rubyArray.size(); i++) {
                JavaClass componentType = component_type();
                IRubyObject wrappedComponentArray = componentType.javaArrayFromRubyArray(context, rubyArray.eltInternal(i));
                javaArray.setWithExceptionHandling(i, JavaUtil.unwrapJavaObject(wrappedComponentArray));
            }
        } else {
            ArrayJavaAddons.copyDataToJavaArray(context, rubyArray, javaArray);
        }
        
        RubyClass proxyClass = (RubyClass)Java.get_proxy_class(javaArray, array_class());

        ArrayJavaProxy proxy = new ArrayJavaProxy(runtime, proxyClass);
        proxy.dataWrapStruct(javaArray);
        
        return proxy;
    }

    @JRubyMethod
    public RubyArray fields() {
        return buildFieldResults(javaClass().getFields());
    }

    @JRubyMethod
    public RubyArray declared_fields() {
        return buildFieldResults(javaClass().getDeclaredFields());
    }

    private RubyArray buildFieldResults(Field[] fields) {
        RubyArray result = getRuntime().newArray(fields.length);
        for (int i = 0; i < fields.length; i++) {
            result.append(new JavaField(getRuntime(), fields[i]));
        }
        return result;
    }

    @JRubyMethod(required = 1)
    public JavaField field(IRubyObject name) {
        String stringName = name.asJavaString();
        Field field = null;
        try {
            field = javaClass().getField(stringName);
            return new JavaField(getRuntime(), field);
        } catch (NoSuchFieldException nsfe) {
            String newName = JavaUtil.getJavaCasedName(stringName);
            if(newName != null) {
                try {
                    field = javaClass().getField(newName);
                    return new JavaField(getRuntime(), field);
                } catch (NoSuchFieldException nsfe2) {}
            }
            throw undefinedFieldError(stringName);
         }
    }

    @JRubyMethod(required = 1)
    public JavaField declared_field(IRubyObject name) {
        String stringName = name.asJavaString();
        Field field = null;
        try {
            field = javaClass().getDeclaredField(stringName);
            return new JavaField(getRuntime(), field);
        } catch (NoSuchFieldException nsfe) {
            String newName = JavaUtil.getJavaCasedName(stringName);
            if(newName != null) {
                try {
                    field = javaClass().getDeclaredField(newName);
                    return new JavaField(getRuntime(), field);
                } catch (NoSuchFieldException nsfe2) {}
            }
            throw undefinedFieldError(stringName);
        }
    }

    private RaiseException undefinedFieldError(String name) {
        return getRuntime().newNameError("undefined field '" + name + "' for class '" + javaClass().getName() + "'", name);
    }

    @JRubyMethod
    public RubyArray interfaces() {
        return JavaClass.getRubyArray(getRuntime(), javaClass().getInterfaces());
    }

    @JRubyMethod(name = "primitive?")
    public RubyBoolean primitive_p() {
        return getRuntime().newBoolean(isPrimitive());
    }

    @JRubyMethod(name = "assignable_from?", required = 1)
    public RubyBoolean assignable_from_p(IRubyObject other) {
        if (! (other instanceof JavaClass)) {
            throw getRuntime().newTypeError("assignable_from requires JavaClass (" + other.getType() + " given)");
        }

        Class<?> otherClass = ((JavaClass) other).javaClass();
        return assignable(javaClass(), otherClass) ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    static boolean assignable(Class<?> thisClass, Class<?> otherClass) {
        if(!thisClass.isPrimitive() && otherClass == Void.TYPE ||
            thisClass.isAssignableFrom(otherClass)) {
            return true;
        }

        otherClass = JavaUtil.primitiveToWrapper(otherClass);
        thisClass = JavaUtil.primitiveToWrapper(thisClass);

        if(thisClass.isAssignableFrom(otherClass)) {
            return true;
        }
        if(Number.class.isAssignableFrom(thisClass)) {
            if(Number.class.isAssignableFrom(otherClass)) {
                return true;
            }
            if(otherClass.equals(Character.class)) {
                return true;
            }
        }
        if(thisClass.equals(Character.class)) {
            if(Number.class.isAssignableFrom(otherClass)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPrimitive() {
        return javaClass().isPrimitive();
    }

    @JRubyMethod
    public JavaClass component_type() {
        if (! javaClass().isArray()) {
            throw getRuntime().newTypeError("not a java array-class");
        }
        return JavaClass.get(getRuntime(), javaClass().getComponentType());
    }
    
}
