/*
 * RubyGlobalEntry.java - No description
 * Created on 16. September 2001, 17:26
 * 
 * Copyright (C) 2001 Jan Arne Petersen, Stefan Matthias Aust, Alan Moore, Benoit Cerrina
 * Jan Arne Petersen <japetersen@web.de>
 * Stefan Matthias Aust <sma@3plus4.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
 * 
 * JRuby - http://jruby.sourceforge.net
 * 
 * This file is part of JRuby
 * 
 * JRuby is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * JRuby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with JRuby; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 */

package org.jruby.runtime;

import org.jruby.*;
import org.jruby.exceptions.*;

/**
 *
 * @author  jpetersen
 * @version
 */
public class RubyGlobalEntry {
    private Ruby ruby = null;

    private String id = null;
    private Object data = null;

    private GetterMethod getter = null;
    private SetterMethod setter = null;

    private static UndefMethods undefMethods = new UndefMethods();
    private static ValueMethods valueMethods = new ValueMethods();
    private static ReadonlySetter readonlySetter = new ReadonlySetter();

    private RubyGlobalEntry(Ruby ruby, String id) {
        this.ruby = ruby;
        this.id = id;

        this.getter = undefMethods;
        this.setter = undefMethods;
    }

    /** Getter for property id.
     * @return Value of property id.
     */
    protected String getId() {
        return id;
    }

    /** Setter for property id.
     * @param id New value of property id.
     */
    protected void setId(String id) {
        this.id = id;
    }

    /** Getter for property data.
     * @return Value of property data.
     */
    protected Object getData() {
        return data;
    }

    /** Setter for property data.
     * @param data New value of property data.
     */
    protected void setData(Object data) {
        this.data = data;
    }

    /** Getter for property getter.
     * @return Value of property getter.
     */
    protected GetterMethod getGetter() {
        return getter;
    }

    /** Setter for property getter.
     * @param getter New value of property getter.
     */
    protected void setGetter(GetterMethod getter) {
        this.getter = getter;
    }

    /** Getter for property setter.
     * @return Value of property setter.
     */
    protected SetterMethod getSetter() {
        return setter;
    }

    /** Setter for property setter.
     * @param setter New value of property setter.
     */
    protected void setSetter(SetterMethod setter) {
        this.setter = setter;
    }

    /** rb_gvar_get
     *
     */
    public RubyObject get() {
        return getGetter().get(getId(), (RubyObject) getData(), this);
    }

    /** rb_gvar_set
     *
     */
    public RubyObject set(RubyObject value) {
        if (ruby.getSecurityLevel() >= 4) {
            throw new RubySecurityException(
                ruby,
                "Insecure: can't change global variable value");
        }

        getSetter().set(value, getId(), (RubyObject) getData(), this);

        return value;
    }

    public boolean isDefined() {
        return !(getter instanceof UndefMethods);
    }

    /** rb_alias_variable
     *
     */
    public void alias(String newId) {
        if (ruby.getSecurityLevel() >= 4) {
            throw new RubySecurityException(ruby, "Insecure: can't alias global variable");
        }

        RubyGlobalEntry entry = getGlobalEntry(ruby, newId);
        entry.data = data;
        entry.getter = getter;
        entry.setter = setter;
    }

    /** rb_global_entry
     *
     */
    public static RubyGlobalEntry getGlobalEntry(Ruby ruby, String id) {
        //Ruby ruby = id.getRuby();

        RubyGlobalEntry entry = (RubyGlobalEntry) ruby.getGlobalMap().get(id);

        if (entry == null) {
            entry = new RubyGlobalEntry(ruby, id);
            ruby.getGlobalMap().put(id, entry);
        }

        return entry;
    }

    public static String getGlobalId(Ruby ruby, String name) {
        if (name.charAt(0) != '$') {
            return '$' + name;
        }
        return name;
    }

    /** defines a global variable with getter and setter methods
     * 
     */
    public static void defineHookedVariable(
        Ruby ruby,
        String name,
        RubyObject value,
        GetterMethod getter,
        SetterMethod setter) {
            
        String id = RubyGlobalEntry.getGlobalId(ruby, name);

        RubyGlobalEntry globalEntry = getGlobalEntry(ruby, id);
        globalEntry.setData(value);
        globalEntry.setGetter(getter != null ? getter : valueMethods);
        globalEntry.setSetter(setter != null ? setter : valueMethods);
    }

    /** defines a global variable
     * 
     */
    public static void defineVariable(Ruby ruby, String name, RubyObject value) {
        defineHookedVariable(ruby, name, value, null, null);
    }

    /** defines a readonly global variable
     * 
     */
    public static void defineReadonlyVariable(
        Ruby ruby,
        String name,
        RubyObject value) {
        defineHookedVariable(ruby, name, value, null, readonlySetter);
    }

    public static void defineVirtualVariable(
        Ruby ruby,
        String name,
        GetterMethod getter,
        SetterMethod setter) {
        getter = getter != null ? getter : valueMethods;
        setter = setter != null ? setter : readonlySetter;

        defineHookedVariable(ruby, name, null, getter, setter);
    }

    public interface GetterMethod {
        public RubyObject get(String id, RubyObject value, RubyGlobalEntry entry);
    }

    public interface SetterMethod {
        public void set(
            RubyObject value,
            String id,
            Object data,
            RubyGlobalEntry entry);
    }

    private static class UndefMethods implements GetterMethod, SetterMethod {
        public RubyObject get(String id, RubyObject value, RubyGlobalEntry entry) {
            // +++ jpetersen
            /*if (ruby.isVerbose()) {
            	ruby.warn("global variable '" + id.toName() + "' not initialized");
            }*/
            // ---

            return entry.ruby.getNil();
        }

        public void set(
            RubyObject value,
            String id,
            Object data,
            RubyGlobalEntry entry) {
            entry.setData(value);

            entry.setGetter(valueMethods);
            entry.setSetter(valueMethods);
        }
    }

    private static class ValueMethods implements GetterMethod, SetterMethod {
        public RubyObject get(String id, RubyObject value, RubyGlobalEntry entry) {
            return value;
        }

        public void set(
            RubyObject value,
            String id,
            Object data,
            RubyGlobalEntry entry) {
            entry.setData(value);
        }
    }

    private static class ReadonlySetter implements SetterMethod {
        public void set(
            RubyObject value,
            String id,
            Object data,
            RubyGlobalEntry entry) {
                
            throw new RubyNameException(entry.ruby, "can't set variable " + id);
        }
    }
}