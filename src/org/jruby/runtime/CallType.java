package org.jruby.runtime;

import org.jruby.exceptions.NameError;

// FIXME replace with enum in Java 5.0
public final class CallType {
    // Call with explicit receiver
    public static final CallType NORMAL = new CallType();
    // Call with implicit receiver (self)
    public static final CallType FUNCTIONAL = new CallType();
    // Call to a super-method
    public static final CallType SUPER = new CallType();
    // Call without any arguments
    public static final CallType VARIABLE = new CallType();

    private CallType() {
        // nobody should create a CallType
    }

    public boolean isNormal() {
        return this == NORMAL;
    }

    public void registerCallStatus(LastCallStatus lastCallStatus, String name) {
        if (this == SUPER) {
            throw new NameError(lastCallStatus.getRuntime(), "super: no superclass method '" + name + "'");
        } else if (this == VARIABLE) {
            lastCallStatus.setVariable();
        } else {
            lastCallStatus.setNormal();
        }
    }
}