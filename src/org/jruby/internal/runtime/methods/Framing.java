package org.jruby.internal.runtime.methods;

public class Framing {
//    Full(EnumSet.allOf(FrameField.class)),
//    Backtrace(EnumSet.of(METHODNAME, FILENAME, LINE)),
//    None(EnumSet.noneOf(FrameField.class));
    public static final Framing Full = new Framing();
    public static final Framing Backtrace = new Framing();
    public static final Framing None = new Framing();

    Framing(){//EnumSet<FrameField> frameField) {
    }
}