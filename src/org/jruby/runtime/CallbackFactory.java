package org.jruby.runtime;

import org.jruby.runtime.callback.Callback;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * Helper class to build Callback method.
 * This impements method corresponding to the signature of method most often found in
 * the Ruby library, for methods with other signature the appropriate Callback object
 * will need to be explicitly created.
 **/
public abstract class CallbackFactory {
    public static final Class[] NULL_CLASS_ARRAY = new Class[0];
    
    /**
     * gets an instance method with no arguments.
     * @param type java class where the method is implemented
     * @param method name of the method
     * @return a CallBack object corresponding to the appropriate method
     **/
    public abstract Callback getMethod(Class type, String method);

    /**
     * gets an instance method with 1 argument.
     * @param type java class where the method is implemented
     * @param method name of the method
     * @param arg1 the class of the only argument for this method
     * @return a CallBack object corresponding to the appropriate method
     **/
    public abstract Callback getMethod(Class type, String method, Class arg1);

    /**
     * gets an instance method with two arguments.
     * @param type java class where the method is implemented
     * @param method name of the method
     * @param arg1 the java class of the first argument for this method
     * @param arg2 the java class of the second argument for this method
     * @return a CallBack object corresponding to the appropriate method
     **/
    public abstract Callback getMethod(Class type, String method, Class arg1, Class arg2);

    /**
     * gets a singleton (class) method without arguments.
     * @param type java class where the method is implemented
     * @param method name of the method
     * @return a CallBack object corresponding to the appropriate method
     **/
    public abstract Callback getSingletonMethod(Class type, String method);

    /**
     * gets a singleton (class) method with 1 argument.
     * @param type java class where the method is implemented
     * @param method name of the method
     * @param arg1 the class of the only argument for this method
     * @return a CallBack object corresponding to the appropriate method
     **/
    public abstract Callback getSingletonMethod(Class type, String method, Class arg1);

    /**
     * gets a singleton (class) method with 2 arguments.
     * @param type java class where the method is implemented
     * @param method name of the method
     * @return a CallBack object corresponding to the appropriate method
     **/
    public abstract Callback getSingletonMethod(Class type, String method, Class arg1, Class arg2);

    public abstract Callback getBlockMethod(Class type, String method);

    /**
     * gets a singleton (class) method with 1 mandatory argument and some optional arguments.
     * @param type java class where the method is implemented
     * @param method name of the method
     * @param arg1 the class of the only mandatory argument for this method
     * @return a CallBack object corresponding to the appropriate method
     **/
    public abstract Callback getOptSingletonMethod(Class type, String method, Class arg1);

    /**
     * gets a singleton (class) method with several mandatory argument and some optional arguments.
     * @param type java class where the method is implemented
     * @param method name of the method
     * @param args an array of java class of the mandatory arguments (NOTE: this must include 
     * the appropriate rest argument class (usually a RubyObject[].class))
     * @return a CallBack object corresponding to the appropriate method
     **/
    public abstract Callback getOptSingletonMethod(Class type, String method, Class[] args);

    /**
    * gets a singleton (class) method with no mandatory argument and some optional arguments.
    * @param type java class where the method is implemented
    * @param method name of the method
    * @return a CallBack object corresponding to the appropriate method
    **/
    public abstract Callback getOptSingletonMethod(Class type, String method);

    /**
    * gets an instance method with no mandatory argument and some optional arguments.
    * @param type java class where the method is implemented
    * @param method name of the method
    * @return a CallBack object corresponding to the appropriate method
    **/
    public abstract Callback getOptMethod(Class type, String method);

    /**
    * gets an instance method with 1 mandatory argument and some optional arguments.
    * @param type java class where the method is implemented
    * @param method name of the method
    * @param arg1 the class of the only mandatory argument for this method
    * @return a CallBack object corresponding to the appropriate method
    **/
    public abstract Callback getOptMethod(Class type, String method, Class arg1);

    public Callback getFalseMethod(final int arity) {
        return new Callback() {
            public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
                return recv.getRuntime().getFalse();
            }

            public Arity getArity() {
                return Arity.createArity(arity);
            }
        };
    }

    public Callback getTrueMethod(final int arity) {
        return new Callback() {
            public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
                return recv.getRuntime().getTrue();
            }

            public Arity getArity() {
                return Arity.createArity(arity);
            }
        };
    }

    public Callback getNilMethod(final int arity) {
        return new Callback() {
            public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
                return recv.getRuntime().getNil();
            }

            public Arity getArity() {
                return Arity.createArity(arity);
            }
        };
    }

    public Callback getSelfMethod(final int arity) {
        return new Callback() {
            public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
                return recv;
            }

            public Arity getArity() {
                return Arity.createArity(arity);
            }
        };
    }
}
