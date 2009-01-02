/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jruby.compiler;

import java.math.BigInteger;
import org.jruby.ast.NodeType;
import org.jruby.compiler.impl.BaseBodyCompiler;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.CallType;
import org.jruby.util.ByteList;

/**
 *
 * @author headius
 */
public interface CacheCompiler {
    public void cacheCallSite(BaseBodyCompiler method, String name, CallType callType);
    
    public void cacheByteList(BaseBodyCompiler method, ByteList contents);
    
    public void cacheSymbol(BaseBodyCompiler method, String symbol);
    
    public void cacheFixnum(BaseBodyCompiler method, long value);
    
    public void cacheBigInteger(BaseBodyCompiler method, BigInteger bigint);

    public void cacheRegexp(BaseBodyCompiler method, String pattern, int options);
    
    public void cacheClosure(BaseBodyCompiler method, String closureMethod, int arity, StaticScope scope, boolean hasMultipleArgsHead, NodeType argsNodeId, ASTInspector inspector);
    
    public void cacheSpecialClosure(BaseBodyCompiler method, String closureMethod);

    public void cacheConstant(BaseBodyCompiler method, String constantName);

    public void cacheStaticScope(BaseBodyCompiler method, StaticScope scope);

    public void finish();
}
