/*
 * RubyHash.java - No description
 * Created on 04. Juli 2001, 22:53
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen, Alan Moore, Benoit Cerrina
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
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

package org.jruby;

import java.util.*;

import org.jruby.exceptions.*;
import org.jruby.runtime.*;
import org.jruby.util.*;

/** Implementation of the Hash class.
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class RubyHash extends RubyObject {
    private RubyMap valueMap;
    private RubyObject defaultValue;

    public RubyHash(Ruby ruby) {
        this(ruby, ruby.getNil());
    }

    public RubyHash(Ruby ruby, RubyObject defaultValue) {
		this(ruby, new RubyHashMap(), defaultValue);
    }

    public RubyHash(Ruby ruby, Map valueMap, RubyObject defaultValue) {
        super(ruby, ruby.getRubyClass("Hash"));
        this.valueMap = new RubyHashMap(valueMap);
        this.defaultValue = defaultValue;
    }

    public RubyObject getDefaultValue() {
        return (defaultValue == null) ? getRuby().getNil() : defaultValue;
    }

    public void setDefaultValue(RubyObject defaultValue) {
        this.defaultValue = defaultValue;
    }

    public RubyMap getValueMap() {
        return valueMap;
    }

    public void setValueMap(RubyMap valueMap) {
        this.valueMap = valueMap;
    }

	/**
	 * gets an iterator on a copy of the keySet.
	 * modifying the iterator will NOT modify the map.
	 * @return the iterator
	 **/
	private Iterator keyIterator() {
		return new ArrayList(valueMap.keySet()).iterator();
	}

	/**
	 * gets an iterator on the keySet.
	 * modifying the iterator WILL modify the map.
	 * @return the iterator
	 **/
	private Iterator modifiableKeyIterator() {
		return valueMap.keySet().iterator();
	}

	private Iterator valueIterator() {
		return new ArrayList(valueMap.values()).iterator();
	}

	/**
	 * gets an iterator on the entries.
	 * modifying this iterator WILL modify the map.
	 * @return the iterator
	 */
	private Iterator entryIterator() {
		//return new ArrayList(valueMap.entrySet()).iterator();		//in general we either want to modify the map or make sure we don't when we use this, so skip the copy
		return valueMap.entrySet().iterator();
	}

    public static RubyClass createHashClass(Ruby ruby) {
        RubyClass hashClass = ruby.defineClass("Hash", ruby.getClasses().getObjectClass());
        hashClass.includeModule(ruby.getClasses().getEnumerableModule());

        hashClass.defineSingletonMethod("new", CallbackFactory.getOptSingletonMethod(RubyHash.class, "newInstance"));
        hashClass.defineSingletonMethod("[]", CallbackFactory.getOptSingletonMethod(RubyHash.class, "create"));
        hashClass.defineMethod("initialize", CallbackFactory.getOptMethod(RubyHash.class, "initialize"));
		hashClass.defineMethod("clone", CallbackFactory.getMethod(RubyHash.class, "rbClone"));

        //    rb_define_method(rb_cHash,"rehash", rb_hash_rehash, 0);

        hashClass.defineMethod("to_hash", CallbackFactory.getMethod(RubyHash.class, "to_hash"));
        hashClass.defineMethod("to_a", CallbackFactory.getMethod(RubyHash.class, "to_a"));
        hashClass.defineMethod("to_s", CallbackFactory.getMethod(RubyHash.class, "to_s"));
        hashClass.defineMethod("inspect", CallbackFactory.getMethod(RubyHash.class, "inspect"));

        hashClass.defineMethod("==", CallbackFactory.getMethod(RubyHash.class, "equal", RubyObject.class));
        hashClass.defineMethod("[]", CallbackFactory.getMethod(RubyHash.class, "aref", RubyObject.class));
        //    rb_define_method(rb_cHash,"fetch", rb_hash_fetch, -1);
        hashClass.defineMethod("[]=", CallbackFactory.getMethod(RubyHash.class, "aset", RubyObject.class, RubyObject.class));
        hashClass.defineMethod("store", CallbackFactory.getMethod(RubyHash.class, "aset", RubyObject.class, RubyObject.class));
		hashClass.defineMethod("default", CallbackFactory.getMethod(RubyHash.class, "getDefaultValue"));
		hashClass.defineMethod("default=", CallbackFactory.getMethod(RubyHash.class, "setDefaultValue", RubyObject.class));
        //    rb_define_method(rb_cHash,"index", rb_hash_index, 1);
        hashClass.defineMethod("indexes", CallbackFactory.getOptMethod(RubyHash.class, "indexes"));
        hashClass.defineMethod("indices", CallbackFactory.getOptMethod(RubyHash.class, "indexes"));
        hashClass.defineMethod("size", CallbackFactory.getMethod(RubyHash.class, "size"));
        hashClass.defineMethod("length", CallbackFactory.getMethod(RubyHash.class, "size"));
        hashClass.defineMethod("empty?", CallbackFactory.getMethod(RubyHash.class, "empty_p"));
		hashClass.defineMethod("each", CallbackFactory.getMethod(RubyHash.class, "each"));
		hashClass.defineMethod("each_pair", CallbackFactory.getMethod(RubyHash.class, "each"));
		hashClass.defineMethod("each_value", CallbackFactory.getMethod(RubyHash.class, "each_value"));
		hashClass.defineMethod("each_key", CallbackFactory.getMethod(RubyHash.class, "each_key"));
		hashClass.defineMethod("sort", CallbackFactory.getMethod(RubyHash.class, "sort"));
		hashClass.defineMethod("keys", CallbackFactory.getMethod(RubyHash.class, "keys"));
		hashClass.defineMethod("values", CallbackFactory.getMethod(RubyHash.class, "values"));

		hashClass.defineMethod("shift", CallbackFactory.getMethod(RubyHash.class, "shift"));
		hashClass.defineMethod("delete", CallbackFactory.getMethod(RubyHash.class, "delete", RubyObject.class));
		hashClass.defineMethod("delete_if", CallbackFactory.getMethod(RubyHash.class, "delete_if"));
		hashClass.defineMethod("reject", CallbackFactory.getMethod(RubyHash.class, "reject"));
		hashClass.defineMethod("reject!", CallbackFactory.getMethod(RubyHash.class, "reject_bang"));
		hashClass.defineMethod("clear", CallbackFactory.getMethod(RubyHash.class, "clear"));
		hashClass.defineMethod("invert", CallbackFactory.getMethod(RubyHash.class, "invert"));

        //    rb_define_method(rb_cHash,"update", rb_hash_update, 1);
        //    rb_define_method(rb_cHash,"replace", rb_hash_replace, 1);

        hashClass.defineMethod("include?", CallbackFactory.getMethod(RubyHash.class, "has_key", RubyObject.class));
        hashClass.defineMethod("member?", CallbackFactory.getMethod(RubyHash.class, "has_key", RubyObject.class));
        hashClass.defineMethod("has_key?", CallbackFactory.getMethod(RubyHash.class, "has_key", RubyObject.class));
        hashClass.defineMethod("has_value?", CallbackFactory.getMethod(RubyHash.class, "has_value", RubyObject.class));
        hashClass.defineMethod("key?", CallbackFactory.getMethod(RubyHash.class, "has_key", RubyObject.class));
        hashClass.defineMethod("value?", CallbackFactory.getMethod(RubyHash.class, "has_value", RubyObject.class));

        return hashClass;
    }

    /** rb_hash_modify
     *
     */
    public void modify() {
        if (isFrozen()) {
            throw new RubyFrozenException(getRuby(), "Hash");
        }
        if (isTaint() && getRuby().getSafeLevel() >= 4) {
            throw new RubySecurityException(getRuby(), "Insecure: can't modify hash");
        }
    }

    public int length() {
        return valueMap.size();
    }

    // Hash methods

    public static RubyHash newHash(Ruby ruby) {
        return newInstance(ruby, ruby.getRubyClass("Hash"), new RubyObject[0]);
    }

	public static RubyHash newHash(Ruby ruby, Map valueMap, RubyObject defaultValue) {
		return new RubyHash(ruby, valueMap, defaultValue);
	}

    public static RubyHash newInstance(Ruby ruby, RubyObject recv, RubyObject[] args) {
        RubyHash hash = new RubyHash(ruby);
        hash.setRubyClass((RubyClass) recv);

        hash.callInit(args);

        return hash;
    }

    public static RubyHash create(Ruby ruby, RubyObject recv, RubyObject[] args) {
        RubyHash hsh = new RubyHash(ruby);
        if (args.length == 1) {
            hsh.setValueMap(new RubyHashMap(((RubyHash) args[0]).getValueMap()));
        } else if (args.length % 2 != 0) {
            throw new ArgumentError(ruby, "odd number of args for Hash");
        } else {
            for (int i = 0; i < args.length; i += 2) {
                hsh.aset(args[i], args[i + 1]);
            }
        }
        return hsh;
    }

    public RubyObject initialize(RubyObject[] args) {
        if (args.length > 0) {
            modify();

            setDefaultValue(args[0]);
        }
        return this;
    }

    public RubyString inspect() {
        final String sep = ", ";
        final String arrow = "=>";
        
        final StringBuffer sb = new StringBuffer("{");

        valueMap.foreach(new RubyMapMethod() {
            boolean firstEntry = true;
            public int execute(Object key, Object value, Object arg) {
                // RubyString str = RubyString.stringValue((RubyObject) arg);
                if (!firstEntry) {
                    sb.append(sep);
                }
                sb.append(((RubyObject) key).funcall("inspect"));
                sb.append(arrow);
                sb.append(((RubyObject) value).funcall("inspect"));
                firstEntry = false;
                return RubyMapMethod.CONTINUE;
            }
        }, null);

        sb.append("}");
        return RubyString.newString(ruby, sb.toString());
    }

    public RubyFixnum size() {
        return RubyFixnum.newFixnum(getRuby(), length());
    }

    public RubyBoolean empty_p() {
        return length() == 0 ? getRuby().getTrue() : getRuby().getFalse();
    }

    public RubyArray to_a() {
        RubyArray result = RubyArray.newArray(getRuby(), length());
        valueMap.foreach(new RubyMapMethod() {
            public int execute(Object key, Object value, Object arg) {
                RubyArray ary = RubyArray.arrayValue((RubyObject) arg);
                ary.push(RubyArray.newArray(getRuby(), (RubyObject) key, (RubyObject) value));
                return RubyMapMethod.CONTINUE;
            }
        }, result);
        return result;
    }

    public RubyString to_s() {
        return to_a().to_s();
    }

	public RubyObject rbClone() {
		RubyHash result = newHash(ruby, getValueMap(), getDefaultValue());
		result.setupClone(this);
		return result;
	}

    public RubyHash to_hash() {
        return this;
    }

    public RubyObject aset(RubyObject key, RubyObject value) {
        modify();

        if (!(key instanceof RubyString) || valueMap.get(key) != null) {
            valueMap.put(key, value);
        } else {
            RubyObject realKey = ((RubyString) key).dup();
            realKey.setFrozen(true);
            valueMap.put(realKey, value);
        }
        return this;
    }

    public RubyObject aref(RubyObject key) {
        RubyObject value = (RubyObject) valueMap.get(key);

        return value != null ? value : getDefaultValue();
    }
    
    public RubyBoolean has_key(RubyObject key) {
        return RubyBoolean.newBoolean(ruby, valueMap.containsKey(key));
    }

    public RubyBoolean has_value(RubyObject value) {
        return RubyBoolean.newBoolean(ruby, valueMap.containsValue(value));
    }

    public RubyHash each() {
        Iterator iter = entryIterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
			ruby.yield(RubyArray.newArray(ruby, (RubyObject)entry.getKey(), (RubyObject)entry.getValue()));
        }
        return this;
    }

	public RubyHash each_value() {
		Iterator iter = valueIterator();
		while (iter.hasNext()) {
			RubyObject value = (RubyObject) iter.next();
			ruby.yield(value);
		}
		return this;
	}

	public RubyHash each_key() {
		Iterator iter = keyIterator();
		while (iter.hasNext()) {
			RubyObject key = (RubyObject) iter.next();
			ruby.yield(key);
		}
		return this;
	}

	public RubyArray sort() {
		RubyArray result = to_a();
		result.sort_bang();
		return result;
	}

    public RubyArray indexes(RubyObject[] indices) {
        ArrayList values = new ArrayList(indices.length);
        
        for (int i = 0; i < indices.length; i++) {
            values.add(aref(indices[i]));
        }
        
        return RubyArray.newArray(ruby, values);
    }

    public RubyArray keys() {
        return RubyArray.newArray(ruby, new ArrayList(valueMap.keySet()));
    }

    public RubyArray values() {
        return RubyArray.newArray(ruby, new ArrayList(valueMap.values()));
    }

    public RubyBoolean equal(RubyObject other) {
        if (this == other) {
            return ruby.getTrue();
        } else if (!(other instanceof RubyHash)) {
            return ruby.getFalse();
        } else if (length() != ((RubyHash)other).length()) {
            return ruby.getFalse();
        }

        // +++
        Iterator iter = entryIterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            
            Object value = ((RubyHash)other).valueMap.get(entry.getKey());
            if (value == null || !entry.getValue().equals(value)) {
                return ruby.getFalse();
            }
        }
        return ruby.getTrue();
        // ---
    }

    public RubyArray shift() {
		modify();
        Iterator iter = entryIterator();
        Map.Entry entry = (Map.Entry)iter.next();
        iter.remove();
		return RubyArray.newArray(ruby, (RubyObject)entry.getKey(), (RubyObject)entry.getValue());
    }

	public RubyObject delete(RubyObject key) {
		modify();
		RubyObject result = (RubyObject) valueMap.remove(key);
		if (result != null) {
			return result;
		}
		if (ruby.isBlockGiven()) {
			return ruby.yield(key);
		} else {
			return getDefaultValue();
		}
	}

	public RubyHash delete_if() {
//		modify();		//Benoit: not needed, it is done in the reject_bang method
		reject_bang();
		return this;
	}

	public RubyHash reject() {
		RubyHash result = (RubyHash) dup();
		result.reject_bang();
		return result;
	}

	public RubyObject reject_bang() {
		modify();
		boolean isModified = false;
		Iterator iter = modifiableKeyIterator();
		while (iter.hasNext()) {
			RubyObject key = (RubyObject) iter.next();
			RubyObject value = (RubyObject) valueMap.get(key);
			RubyObject shouldDelete = ruby.yield(RubyArray.newArray(ruby, key, value));
			if (shouldDelete.isTrue()) {
				iter.remove();
				isModified = true;
			}
		}
		if (isModified) {
			return this;
		} else {
			return ruby.getNil();
		}
	}

	public RubyHash clear() {
		modify();
		valueMap.clear();
		return this;
	}

	public RubyHash invert() {
		RubyHash result = newHash(ruby);
		Iterator iter = entryIterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			RubyObject key = (RubyObject) entry.getKey();
			RubyObject value = (RubyObject) entry.getValue();
			result.aset(value, key);
		}
		return result;
	}


	public void marshalTo(MarshalStream output) throws java.io.IOException {
		output.write('{');
		output.dumpInt(getValueMap().size());
		Iterator iter = entryIterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			RubyObject key = (RubyObject) entry.getKey();
			RubyObject value = (RubyObject) entry.getValue();
			output.dumpObject(key);
			output.dumpObject(value);
		}
	}
}
