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
 * Copyright (C) 2001 Chad Fowler <chadfowler@chadfowler.com>
 * Copyright (C) 2001 Alan Moore <alan_moore@gmx.net>
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004-2006 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2006 Ola Bini <Ola.Bini@ki.se>
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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.jruby.javasupport.JavaUtil;
import org.jruby.javasupport.util.ConversionIterator;
import org.jruby.runtime.Arity;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.jruby.runtime.marshal.MarshalStream;
import org.jruby.runtime.marshal.UnmarshalStream;

/** Implementation of the Hash class.
 *
 * @author  jpetersen
 */
public class RubyHash extends RubyObject implements Map {
    private Map valueMap;
    // Place we capture any explicitly set proc so we can return it for default_proc
    private IRubyObject capturedDefaultProc;
    
    // Holds either default value or default proc.  Executing whatever is here will return the
    // correct default value.
    private Callback defaultValueCallback;
    
    private boolean isRehashing = false;

    public RubyHash(IRuby runtime) {
        this(runtime, runtime.getNil());
    }

    public RubyHash(IRuby runtime, IRubyObject defaultValue) {
        this(runtime, new HashMap(), defaultValue);
    }

    public RubyHash(IRuby runtime, Map valueMap, IRubyObject defaultValue) {
        super(runtime, runtime.getClass("Hash"));
        this.valueMap = new HashMap(valueMap);
        this.capturedDefaultProc = runtime.getNil();
        setDefaultValue(defaultValue);
    }

    public static RubyHash nilHash(IRuby runtime) {
        return new RubyHash(runtime) {
            public boolean isNil() {
                return true;
            }
        };
    }
    
    public IRubyObject getDefaultValue(IRubyObject[] args) {
        return defaultValueCallback != null ? defaultValueCallback.execute(this, args) : getRuntime().getNil();
    }

    public IRubyObject setDefaultValue(final IRubyObject defaultValue) {
        capturedDefaultProc = getRuntime().getNil();
        defaultValueCallback = new Callback() {
            public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
                return defaultValue;
            }

            public Arity getArity() {
                return Arity.optional();
            }
        };
        
        return defaultValue;
    }

    public void setDefaultProc(final RubyProc newProc) {
        final IRubyObject self = this;
        capturedDefaultProc = newProc;
        defaultValueCallback = new Callback() {
            public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
                IRubyObject[] nargs = args.length == 0 ? new IRubyObject[] { self } :
                     new IRubyObject[] { self, args[0] };

                return newProc.call(nargs);
            }

            public Arity getArity() {
                return Arity.optional();
            }
        };
    }
    
    public IRubyObject default_proc() {
        return capturedDefaultProc;
    }

    public Map getValueMap() {
        return valueMap;
    }

    public void setValueMap(Map valueMap) {
        this.valueMap = valueMap;
    }

	/**
	 * gets an iterator on a copy of the keySet.
	 * modifying the iterator will NOT modify the map.
	 * if the map is modified while iterating on this iterator, the iterator
	 * will not be invalidated but the content will be the same as the old one.
	 * @return the iterator
	 **/
	private Iterator keyIterator() {
		return new ArrayList(valueMap.keySet()).iterator();
	}

	private Iterator valueIterator() {
		return new ArrayList(valueMap.values()).iterator();
	}


	/**
	 * gets an iterator on the entries.
	 * modifying this iterator WILL modify the map.
	 * the iterator will be invalidated if the map is modified.
	 * @return the iterator
	 */
	private Iterator modifiableEntryIterator() {
		return valueMap.entrySet().iterator();
	}

	/**
	 * gets an iterator on a copy of the entries.
	 * modifying this iterator will NOT modify the map.
	 * if the map is modified while iterating on this iterator, the iterator
	 * will not be invalidated but the content will be the same as the old one.
	 * @return the iterator
	 */
	private Iterator entryIterator() {
		return new ArrayList(valueMap.entrySet()).iterator();		//in general we either want to modify the map or make sure we don't when we use this, so skip the copy
	}

    /** rb_hash_modify
     *
     */
    public void modify() {
    	testFrozen("Hash");
        if (isTaint() && getRuntime().getSafeLevel() >= 4) {
            throw getRuntime().newSecurityError("Insecure: can't modify hash");
        }
    }

    private int length() {
        return valueMap.size();
    }

    // Hash methods

    public static RubyHash newHash(IRuby runtime) {
    	return new RubyHash(runtime);
    }

	public static RubyHash newHash(IRuby runtime, Map valueMap, IRubyObject defaultValue) {
		assert defaultValue != null;
		
		return new RubyHash(runtime, valueMap, defaultValue);
	}

    public IRubyObject initialize(IRubyObject[] args) {
        if (args.length > 0) {
            modify();

            setDefaultValue(args[0]);
        }
        return this;
    }
    
    public IRubyObject inspect() {
        final String sep = ", ";
        final String arrow = "=>";
        final StringBuffer sb = new StringBuffer("{");
        boolean firstEntry = true;
        
        for (Iterator iter = valueMap.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry entry = (Map.Entry) iter.next();
            IRubyObject key = (IRubyObject) entry.getKey();
            IRubyObject value = (IRubyObject) entry.getValue();
            if (!firstEntry) {
                sb.append(sep);
            }
            sb.append(key.callMethod("inspect")).append(arrow);
            sb.append(value.callMethod("inspect"));
            firstEntry = false;
        }
        sb.append("}");
        return getRuntime().newString(sb.toString());
    }

    public RubyFixnum rb_size() {
        return getRuntime().newFixnum(length());
    }

    public RubyBoolean empty_p() {
        return length() == 0 ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    public RubyArray to_a() {
        RubyArray result = getRuntime().newArray(length());
        
        for(Iterator iter = valueMap.entrySet().iterator(); iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();
            IRubyObject key = (IRubyObject) entry.getKey();
            IRubyObject value = (IRubyObject) entry.getValue();
            result.append(getRuntime().newArray(key, value));
        }
        return result;
    }

    public IRubyObject to_s() {
        return to_a().to_s();
    }

	public IRubyObject rbClone() {
		RubyHash result = newHash(getRuntime(), getValueMap(), getDefaultValue(NULL_ARRAY));
		result.setTaint(isTaint());
		result.initCopy(this);
		result.setFrozen(isFrozen());
		return result;
	}

    public RubyHash rehash() {
        modify();
        try {
            isRehashing = true;
            valueMap = new HashMap(valueMap);
        } finally {
            isRehashing = false;
        }
        return this;
    }

    public RubyHash to_hash() {
        return this;
    }

    public IRubyObject aset(IRubyObject key, IRubyObject value) {
        modify();
        
        if (!(key instanceof RubyString) || valueMap.get(key) != null) {
            valueMap.put(key, value);
        } else {
            IRubyObject realKey = key.dup();
            realKey.setFrozen(true);
            valueMap.put(realKey, value);
        }
        return value;
    }

    public IRubyObject aref(IRubyObject key) {
        IRubyObject value = (IRubyObject) valueMap.get(key);

        return value != null ? value : callMethod("default", new IRubyObject[] {key});
    }

    public IRubyObject fetch(IRubyObject[] args) {
        if (args.length < 1) {
            throw getRuntime().newArgumentError(args.length, 1);
        }
        IRubyObject key = args[0];
        IRubyObject result = (IRubyObject) valueMap.get(key);
        if (result == null) {
            if (args.length > 1) {
                return args[1];
            } else if (getRuntime().getCurrentContext().isBlockGiven()) {
                return getRuntime().getCurrentContext().yield(key);
            } 

            throw getRuntime().newIndexError("key not found");
        }
        return result;
    }


    public RubyBoolean has_key(IRubyObject key) {
        return getRuntime().newBoolean(valueMap.containsKey(key));
    }

    public RubyBoolean has_value(IRubyObject value) {
        return getRuntime().newBoolean(valueMap.containsValue(value));
    }

    public RubyHash each() {
        for (Iterator iter = entryIterator(); iter.hasNext();) {
            checkRehashing();
            Map.Entry entry = (Map.Entry) iter.next();
			getRuntime().getCurrentContext().yield(getRuntime().newArray((IRubyObject)entry.getKey(), (IRubyObject)entry.getValue()), null, null, true);
        }
        return this;
    }

    private void checkRehashing() {
        if (isRehashing) {
            throw getRuntime().newIndexError("rehash occured during iteration");
        }
    }

    public RubyHash each_value() {
		for (Iterator iter = valueIterator(); iter.hasNext();) {
            checkRehashing();
			IRubyObject value = (IRubyObject) iter.next();
			getRuntime().getCurrentContext().yield(value);
		}
		return this;
	}

	public RubyHash each_key() {
		for (Iterator iter = keyIterator(); iter.hasNext();) {
			checkRehashing();
            IRubyObject key = (IRubyObject) iter.next();
			getRuntime().getCurrentContext().yield(key);
		}
		return this;
	}

	public RubyArray sort() {
		return (RubyArray) to_a().sort_bang();
	}

    public IRubyObject index(IRubyObject value) {
        for (Iterator iter = valueMap.keySet().iterator(); iter.hasNext(); ) {
            Object key = iter.next();
            if (value.equals(valueMap.get(key))) {
                return (IRubyObject) key;
            }
        }
        return getRuntime().getNil();
    }

    public RubyArray indices(IRubyObject[] indices) {
        ArrayList values = new ArrayList(indices.length);

        for (int i = 0; i < indices.length; i++) {
            values.add(aref(indices[i]));
        }

        return getRuntime().newArray(values);
    }

    public RubyArray keys() {
        return getRuntime().newArray(new ArrayList(valueMap.keySet()));
    }

    public RubyArray rb_values() {
        return getRuntime().newArray(new ArrayList(valueMap.values()));
    }

    public IRubyObject equal(IRubyObject other) {
        if (this == other) {
            return getRuntime().getTrue();
        } else if (!(other instanceof RubyHash)) {
            return getRuntime().getFalse();
        } else if (length() != ((RubyHash)other).length()) {
            return getRuntime().getFalse();
        }

        for (Iterator iter = modifiableEntryIterator(); iter.hasNext();) {
            checkRehashing();
            Map.Entry entry = (Map.Entry) iter.next();

            Object value = ((RubyHash)other).valueMap.get(entry.getKey());
            if (value == null || !entry.getValue().equals(value)) {
                return getRuntime().getFalse();
            }
        }
        return getRuntime().getTrue();
    }

    public RubyArray shift() {
		modify();
        Iterator iter = modifiableEntryIterator();
        Map.Entry entry = (Map.Entry)iter.next();
        iter.remove();
		return getRuntime().newArray((IRubyObject)entry.getKey(), (IRubyObject)entry.getValue());
    }

	public IRubyObject delete(IRubyObject key) {
		modify();
		IRubyObject result = (IRubyObject) valueMap.remove(key);
		if (result != null) {
			return result;
		} else if (getRuntime().getCurrentContext().isBlockGiven()) {
			return getRuntime().getCurrentContext().yield(key);
		} 

		return getDefaultValue(new IRubyObject[] {key});
	}

	public RubyHash delete_if() {
		reject_bang();
		return this;
	}

	public RubyHash reject() {
		RubyHash result = (RubyHash) dup();
		result.reject_bang();
		return result;
	}

	public RubyHash reject_bang() {
		modify();
		boolean isModified = false;
		for (Iterator iter = keyIterator(); iter.hasNext();) {
			IRubyObject key = (IRubyObject) iter.next();
			IRubyObject value = (IRubyObject) valueMap.get(key);
			IRubyObject shouldDelete = getRuntime().getCurrentContext().yield(getRuntime().newArray(key, value), null, null, true);
			if (shouldDelete.isTrue()) {
				valueMap.remove(key);
				isModified = true;
			}
		}

		return isModified ? this : nilHash(getRuntime()); 
	}

	public RubyHash rb_clear() {
		modify();
		valueMap.clear();
		return this;
	}

	public RubyHash invert() {
		RubyHash result = newHash(getRuntime());
		
		for (Iterator iter = modifiableEntryIterator(); iter.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			result.aset((IRubyObject) entry.getValue(), 
					(IRubyObject) entry.getKey());
		}
		return result;
	}

    public RubyHash update(IRubyObject freshElements) {
        modify();
        RubyHash freshElementsHash =
            (RubyHash) freshElements.convertType(RubyHash.class, "Hash", "to_hash");
        valueMap.putAll(freshElementsHash.valueMap);
        return this;
    }
    
    public RubyHash merge(IRubyObject freshElements) {
        return ((RubyHash) dup()).update(freshElements);
    }

    public RubyHash replace(IRubyObject replacement) {
        modify();
        RubyHash replacementHash =
            (RubyHash) replacement.convertType(RubyHash.class, "Hash", "to_hash");
        valueMap.clear();
        valueMap.putAll(replacementHash.valueMap);
        return this;
    }

    public RubyArray values_at(IRubyObject[] argv) {
        RubyArray result = getRuntime().newArray();
        for (int i = 0; i < argv.length; i++) {
            IRubyObject key = argv[i];
            result.append(aref(key));
        }
        return result;
    }

	public void marshalTo(MarshalStream output) throws java.io.IOException {
		output.write('{');
		output.dumpInt(getValueMap().size());
		
		for (Iterator iter = entryIterator(); iter.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			
			output.dumpObject((IRubyObject) entry.getKey());
			output.dumpObject((IRubyObject) entry.getValue());
		}
	}

    public static RubyHash unmarshalFrom(UnmarshalStream input) throws java.io.IOException {
        RubyHash result = newHash(input.getRuntime());
        input.registerLinkTarget(result);
        int size = input.unmarshalInt();
        for (int i = 0; i < size; i++) {
            IRubyObject key = input.unmarshalObject();
            IRubyObject value = input.unmarshalObject();
            result.aset(key, value);
        }
        return result;
    }

    public Class getJavaClass() {
        return Map.class;
    }
	
    // Satisfy java.util.Set interface (for Java integration)

	public boolean isEmpty() {
		return valueMap.isEmpty();
	}

	public boolean containsKey(Object key) {
		return keySet().contains(key);
	}

	public boolean containsValue(Object value) {
		IRubyObject element = JavaUtil.convertJavaToRuby(getRuntime(), value);
		
		for (Iterator iter = valueMap.values().iterator(); iter.hasNext(); ) {
			if (iter.next().equals(element)) {
				return true;
			}
		}
		return false;
	}

	public Object get(Object key) {
		return JavaUtil.convertRubyToJava((IRubyObject) valueMap.get(JavaUtil.convertJavaToRuby(getRuntime(), key)));
	}

	public Object put(Object key, Object value) {
		return valueMap.put(JavaUtil.convertJavaToRuby(getRuntime(), key),
				JavaUtil.convertJavaToRuby(getRuntime(), value));
	}

	public Object remove(Object key) {
		return valueMap.remove(JavaUtil.convertJavaToRuby(getRuntime(), key));
	}

	public void putAll(Map map) {
		for (Iterator iter = map.keySet().iterator(); iter.hasNext();) {
			Object key = iter.next();
			
			put(key, map.get(key));
		}
	}

	public Set keySet() {
		return new ConversionSet(valueMap.keySet());
	}

	public Set entrySet() {
		// TODO: Set.Entry must be wrapped appropriately...?
		return new ConversionSet(valueMap.entrySet());
	}

	public int size() {
		return valueMap.size();
	}

	public Collection values() {
		// TODO Auto-generated method stub
		return null;
	}

	public void clear() {
		valueMap.clear();
	}
	
	class ConversionSet implements Set {
		private Set set;

		public ConversionSet(Set set) {
			this.set = set;
		}

		public int size() {
			return set.size();
		}

		public boolean isEmpty() {
			return set.isEmpty();
		}

		public boolean contains(Object element) {
			return set.contains(JavaUtil.convertJavaToRuby(getRuntime(), element));
		}

		public Iterator iterator() {
			return new ConversionIterator(set.iterator());
		}

		public Object[] toArray() {
			Object[] array = new Object[size()];
			Iterator iter = iterator();
			
			for (int i = 0; iter.hasNext(); i++) {
				array[i] = iter.next();
			}

			return array;
		}

        public Object[] toArray(final Object[] arg) {
            Object[] array = arg;
            int length = size();
            
            if(array.length < length) {
                Class type = array.getClass().getComponentType();
                array = (Object[]) Array.newInstance(type, length);
            }
            
            Iterator iter = iterator();
            for (int i = 0; iter.hasNext(); i++) {
                array[i] = iter.next();
            }
            
            return array;
        }

		public boolean add(Object element) {
			return set.add(JavaUtil.convertJavaToRuby(getRuntime(), element));
		}

		public boolean remove(Object element) {
			return set.remove(JavaUtil.convertJavaToRuby(getRuntime(), element));
		}

		public boolean containsAll(Collection c) {
			for (Iterator iter = c.iterator(); iter.hasNext();) {
				if (!contains(iter.next())) {
					return false;
				}
			}

			return true;
		}

		public boolean addAll(Collection c) {
			for (Iterator iter = c.iterator(); iter.hasNext(); ) {
				add(iter.next());
			}

			return !c.isEmpty();
		}

		public boolean retainAll(Collection c) {
			boolean listChanged = false;
			
			for (Iterator iter = iterator(); iter.hasNext();) {
				Object element = iter.next();
				if (!c.contains(element)) {
					remove(element);
					listChanged = true;
				}
			}

			return listChanged;
		}

		public boolean removeAll(Collection c) {
			boolean changed = false;
			
			for (Iterator iter = c.iterator(); iter.hasNext();) {
				if (remove(iter.next())) {
					changed = true;
				}
			}

			return changed;
		}

		public void clear() {
			set.clear();
		}
		
	}
}
