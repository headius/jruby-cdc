package org.jruby.javasupport;

import java.lang.reflect.*;
import java.util.*;

import org.jruby.*;
import org.jruby.exceptions.NameError;

public class JavaSupport {
    private Ruby ruby;

    private Map loadedJavaClasses = new HashMap();
    private List importedPackages = new LinkedList();
    private Map renamedJavaClasses = new HashMap();

    private ClassLoader javaClassLoader = ClassLoader.getSystemClassLoader();

    public JavaSupport(Ruby ruby) {
        this.ruby = ruby;
    }

    public RubyModule loadClass(Class javaClass, String rubyName) {
        if (javaClass == Object.class) {
            return ruby.getClasses().getJavaObjectClass();
        }
        if (loadedJavaClasses.containsKey(javaClass)) {
            return (RubyModule) loadedJavaClasses.get(javaClass);
        }
 
        if (rubyName == null) {
            String javaName = javaClass.getName();
            rubyName = javaName.substring(javaName.lastIndexOf('.') + 1);
        }
        if (javaClass.isInterface()) {
            return createRubyInterface(javaClass, rubyName);
        } else {
            return createRubyClass(javaClass, rubyName);
        }
    }

    private RubyClass createRubyClass(Class javaClass, String rubyName) {
        RubyClass superClass = (RubyClass) loadClass(javaClass.getSuperclass(), null);
        RubyClass rubyClass = ruby.defineClass(rubyName, superClass);
        
        defineWrapperMethods(javaClass, rubyClass, true);
        defineConstants(javaClass, rubyClass);
        defineFields(javaClass, rubyClass);
        addDefaultModules(rubyClass);

        loadedJavaClasses.put(javaClass, rubyClass);

        return rubyClass;
    }

    private RubyModule createRubyInterface(Class javaInterface, String rubyName) {
        RubyModule newInterface = ruby.defineModule(rubyName);

        Map methods = getMethodsByName(javaInterface);

        for (Iterator iter = methods.entrySet().iterator(); iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();

            newInterface.defineModuleFunction((String) entry.getKey(),
            new JavaInterfaceMethod((String) entry.getKey(), (Set) entry.getValue()));
        }

        newInterface.defineModuleFunction("new" + rubyName, new JavaInterfaceConstructor(javaInterface));

        return newInterface;
    }

	/**
	 * @return a Map (String --> Set) of methods by name
	 */
	private static Map getMethodsByName(Class javaClass) {
		Method[] methods = javaClass.getMethods();
		List methodList = Arrays.asList(methods);
        Map result = new HashMap(methods.length);

        Iterator iter = methodList.iterator();
        while (iter.hasNext()) {
            Method method = (Method) iter.next();
			String name = method.getName();

            if (! result.containsKey(name)) {
                result.put(name, new HashSet());
            }
            ((Set) result.get(name)).add(method);
        }
        return result;
    }

    private void defineConstants(Class javaClass, RubyClass rubyClass) {
        // add constants
        Field[] fields = javaClass.getFields();

        for (int i = 0; i < fields.length; i++) {
            int modifiers = fields[i].getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                try {
                    String name = fields[i].getName();

                    // Uppercase first character of the constant name
                    name = Character.toUpperCase(name.charAt(0)) + name.substring(1);

                    rubyClass.defineConstant(name, JavaUtil.convertJavaToRuby(ruby, fields[i].get(null)));
                } catch (IllegalAccessException iaExcptn) {
                }
            }
        }
    }

    private void defineFields(Class javaClass, RubyClass rubyClass) {
        Field[] fields = javaClass.getFields();

        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {
                String name = field.getName();

                // Create read access
                if (!rubyClass.isMethodDefined(name)) {
                    ruby.getMethodCache().clearByName(name);
                    rubyClass.defineMethod(name, new JavaFieldReader(field));
                }

                // Create write access
                if (!rubyClass.isMethodDefined(name + "=")) {
                    ruby.getMethodCache().clearByName(name + "=");
                    rubyClass.defineMethod(name + "=", new JavaFieldWriter(field));
                }
            }
        }
    }

    private void defineWrapperMethods(Class javaClass, RubyClass rubyClass, boolean searchSuper) {
        Map methodMap = new HashMap();
        Map singletonMethodMap = new HashMap();

        Method[] methods = javaClass.getDeclaredMethods();

        for (int i = 0; i < methods.length; i++) {
			String methodName = methods[i].getName();
            if (Modifier.isStatic(methods[i].getModifiers())) {
				if (singletonMethodMap.get(methodName) == null) {
                    singletonMethodMap.put(methodName, new LinkedList());
                }
                ((List) singletonMethodMap.get(methodName)).add(methods[i]);
            } else {
                if (methodMap.get(methodName) == null) {
                    methodMap.put(methodName, new LinkedList());
                }
                ((List) methodMap.get(methodName)).add(methods[i]);
            }
        }

        if (javaClass.getConstructors().length > 0) {
            rubyClass.defineSingletonMethod("new", new JavaConstructor(javaClass.getConstructors()));
        } else {
            rubyClass.getSingletonClass().undefMethod("new");
        }

        Iterator iter = methodMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            methods = (Method[]) ((List) entry.getValue()).toArray(new Method[((List) entry.getValue()).size()]);

            String javaName = (String) entry.getKey();
            String rubyName = toRubyName(javaName);

            rubyClass.defineMethod(javaName, new JavaMethod(methods, searchSuper));

            if (!rubyClass.isMethodDefined(rubyName)) {
                rubyClass.defineAlias(rubyName, javaName);
            }
        }

        iter = singletonMethodMap.entrySet().iterator();
		RubyClass singletonClass = rubyClass.getSingletonClass();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            methods = (Method[]) ((List) entry.getValue()).toArray(new Method[((List) entry.getValue()).size()]);
			String javaName = (String) entry.getKey();
            String rubyName = toRubyName(javaName);
            rubyClass.defineSingletonMethod(javaName, new JavaMethod(methods, searchSuper, true));
			if(!singletonClass.isMethodDefined(rubyName))
			{
				singletonClass.defineAlias(rubyName, javaName);
			}
        }

    }

    /**
     * translate java naming convention in ruby naming convention.
     * translate getter and setter in ruby style accessor and 
     * boolean getter in ruby style ? method
     * @param javaName the name of the java method
     * @return the name of the equivalent rubyMethod if a translation
     * was needed null otherwise
     **/
    private String toRubyName(String javaName) {
        if (javaName.equals("get")) {
            return "[]";
        } else if (javaName.equals("set")) {
            return "[]=";
        } else if (javaName.equals("getElementAt")) {
            return "[]";
        } else if (javaName.equals("getValueAt")) {
            return "[]";
        } else if (javaName.equals("setValueAt")) {
            return "[]=";
        } else if (javaName.startsWith("get")) {
            return Character.toLowerCase(javaName.charAt(3)) + javaName.substring(4);
        } else if (javaName.startsWith("is")) {
            return Character.toLowerCase(javaName.charAt(2)) + javaName.substring(3) + "?";
        } else if (javaName.startsWith("can")) {
            return javaName + "?";
        } else if (javaName.startsWith("has")) {
            return javaName + "?";
        } else if (javaName.startsWith("set")) {
            return Character.toLowerCase(javaName.charAt(3)) + javaName.substring(4) + "=";
        } else if (javaName.equals("compareTo")) {
            return "<=>";
        }
        return javaName;
    }

    private void addDefaultModules(RubyClass rubyClass) {
        if (rubyClass.isMethodDefined("hasNext") && rubyClass.isMethodDefined("next")) {
            rubyClass.includeModule(ruby.getClasses().getEnumerableModule());
            rubyClass.defineMethod("each", new JavaEachMethod("hasNext", "next"));
        } else if (rubyClass.isMethodDefined("hasNext") && rubyClass.isMethodDefined("next")) {
            rubyClass.includeModule(ruby.getClasses().getEnumerableModule());
            rubyClass.defineMethod("each", new JavaEachMethod("hasMoreElements", "nextElement"));
        }

        if (rubyClass.isMethodDefined("compareTo")) {
            rubyClass.includeModule(ruby.getClasses().getComparableModule());
            if (!rubyClass.isMethodDefined("<=>")) {
                rubyClass.defineAlias("<=>", "compareTo");
            }
        }
    }

    public Class loadJavaClass(String className) {
        try {
            return javaClassLoader.loadClass(className);
        } catch (ClassNotFoundException cnfExcptn) {
            Iterator iter = importedPackages.iterator();
            while (iter.hasNext()) {
                String packageName = (String) iter.next();
                try {
                    return javaClassLoader.loadClass(packageName + "." + className);
                } catch (ClassNotFoundException cnfExcptn_) {
                }
            }
        }
        throw new NameError(ruby, "cannot load Java class: " + className);
    }

    public void addImportPackage(String packageName) {
        importedPackages.add(packageName);
    }

	public String getJavaName(String rubyName) {
		return (String) renamedJavaClasses.get(rubyName);
	}

	public void rename(String rubyName, String javaName) {
		renamedJavaClasses.put(rubyName, javaName);
	}

    public Class getJavaClass(RubyClass type) {
        Iterator iter = loadedJavaClasses.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            if (entry.getValue() == type) {
                return (Class)entry.getKey();
            }
        }
        return null;
    }
}
