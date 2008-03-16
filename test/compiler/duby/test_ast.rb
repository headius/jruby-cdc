require 'compiler/duby'
require 'test/unit'
require 'jruby'

class TestAst < Test::Unit::TestCase
  def test_args
    node = JRuby.parse("def foo(a, b = 1, *c, &d); end")
    args_node = node.child_nodes[0].next_node.args_node
    
    new_ast = args_node.transform(nil)
    
    assert_not_nil(new_ast)
    inspected = "Arguments\n RequiredArgument(a)\n OptionalArgument(b)\n  LocalAssignment(b)\n   Fixnum(1)\n RestArgument(c)\n BlockArgument(d)"
    assert_equal(inspected, new_ast.inspect)
    
    assert(Compiler::Duby::Arguments === new_ast)
    children = new_ast.children
    assert_equal(4, children.size)
    assert(Array === children[0])
    assert(Compiler::Duby::RequiredArgument === children[0][0])
    assert_equal("a", children[0][0].name)
    assert_equal(new_ast, children[0][0].parent)
    assert(Array === children[1])
    assert(Compiler::Duby::OptionalArgument === children[1][0])
    assert_equal("b", children[1][0].name)
    assert_equal(new_ast, children[1][0].parent)
    # TODO: should be LocalDeclaration soon
    assert(Compiler::Duby::LocalAssignment === children[1][0].child)
    assert_equal(children[1][0], children[1][0].child.parent)
    assert(Compiler::Duby::Fixnum, children[1][0].child.value.parent)
    assert_equal(children[1][0].child, children[1][0].child.value.parent)
    assert(Compiler::Duby::RestArgument === children[2])
    assert_equal("c", children[2].name)
    assert_equal(new_ast, children[2].parent)
    assert(Compiler::Duby::BlockArgument === children[3])
    assert_equal("d", children[3].name)
    assert_equal(new_ast, children[3].parent)
  end
  
  def test_locals
    node = JRuby.parse("a = 1; a")
    new_ast = node.child_nodes[0].transform(nil)
    
    assert_not_nil(new_ast)
    assert(Compiler::Duby::Body === new_ast)
    inspected = "Body\n LocalAssignment(a)\n  Fixnum(1)\n Local(a)"
    assert_equal(inspected, new_ast.inspect)
    assert(!new_ast.newline)
    
    asgn = new_ast[0]
    var = new_ast[1]
    
    assert(Compiler::Duby::LocalAssignment === asgn)
    assert(asgn.newline)
    assert_equal("a", asgn.name)
    assert(Compiler::Duby::Fixnum === asgn.value)
    assert(!asgn.value.newline)
    assert(Compiler::Duby::Local === var)
    assert(var.newline)
    assert_equal("a", var.name)
  end
  
  def test_fields
    node = JRuby.parse("@a = 1; @a")
    new_ast = node.child_nodes[0].transform(nil)
    
    assert_not_nil(new_ast)
    assert(Compiler::Duby::Body === new_ast)
    inspected = "Body\n FieldAssignment(@a)\n  Fixnum(1)\n Field(@a)"
    assert_equal(inspected, new_ast.inspect)
    assert(!new_ast.newline)
    
    asgn = new_ast[0]
    var = new_ast[1]
    
    assert(Compiler::Duby::FieldAssignment === asgn)
    assert(asgn.newline)
    assert_equal("@a", asgn.name)
    assert(Compiler::Duby::Fixnum === asgn.value)
    assert(!asgn.value.newline)
    assert(Compiler::Duby::Field === var)
    assert(var.newline)
    assert_equal("@a", var.name)
  end
  
  def test_array
    node = JRuby.parse("[a = 1, 1]")
    new_ast = node.child_nodes[0].child_nodes[0].transform(nil)
    
    assert_not_nil(new_ast)
    assert(Compiler::Duby::Array === new_ast)
    assert_equal("Array\n LocalAssignment(a)\n  Fixnum(1)\n Fixnum(1)", new_ast.inspect)
    
    assert(Compiler::Duby::LocalAssignment === new_ast[0])
    assert(Compiler::Duby::Fixnum === new_ast[1])
  end
  
  def test_call
    node = JRuby.parse("1.foo(1)")
    new_ast = node.child_nodes[0].child_nodes[0].transform(nil)
    
    assert_not_nil(new_ast)
    assert(Compiler::Duby::Call === new_ast)
    assert_equal("Call(foo)\n Fixnum(1)\n Fixnum(1)", new_ast.inspect)
    
    assert_equal("foo", new_ast.name)
    assert(Compiler::Duby::Fixnum === new_ast.target)
    assert_not_nil(new_ast.parameters)
    assert_equal(1, new_ast.parameters.size)
    assert(Compiler::Duby::Fixnum === new_ast.parameters[0])
  end
  
  def test_fcall
    node = JRuby.parse("foo(1)")
    new_ast = node.child_nodes[0].child_nodes[0].transform(nil)
    
    assert_not_nil(new_ast)
    assert(Compiler::Duby::FunctionalCall === new_ast)
    assert_equal("FunctionalCall(foo)\n Fixnum(1)", new_ast.inspect)
    
    assert_equal("foo", new_ast.name)
    assert_not_nil(new_ast.parameters)
    assert_equal(1, new_ast.parameters.size)
    assert(Compiler::Duby::Fixnum === new_ast.parameters[0])
  end
  
  def test_if
    node = JRuby.parse("if 1; 2; elsif !3; 4; else; 5; end")
    new_ast = node.child_nodes[0].child_nodes[0].transform(nil)
    
    assert_not_nil(new_ast)
    assert(Compiler::Duby::If === new_ast)
    assert_equal("If\n Fixnum(1)\n Fixnum(2)\n If\n  Not\n   Fixnum(3)\n  Fixnum(4)\n  Fixnum(5)", new_ast.inspect)
    
    assert(Compiler::Duby::Fixnum === new_ast.condition)
    assert(Compiler::Duby::Fixnum === new_ast.body)
    assert(Compiler::Duby::If === new_ast.else)
    assert(Compiler::Duby::Not === new_ast.else.condition)
    assert(Compiler::Duby::Fixnum === new_ast.else.body)
    assert(Compiler::Duby::Fixnum === new_ast.else.else)
  end
  
  def test_begin
    node = JRuby.parse("begin; 1; 2; end")
    new_ast = node.child_nodes[0].transform(nil)
    
    assert_not_nil(new_ast)
    assert(Compiler::Duby::Body === new_ast)
    assert(Compiler::Duby::Fixnum === new_ast[0])
    
    node = JRuby.parse("begin; 1; end")
    new_ast = node.child_nodes[0].transform(nil)
    assert(Compiler::Duby::Fixnum === new_ast)
  end
  
  def test_block
    node = JRuby.parse("1; 2")
    new_ast = node.child_nodes[0].transform(nil)
    
    assert_not_nil(new_ast)
    assert(Compiler::Duby::Body === new_ast)
    assert(Compiler::Duby::Fixnum === new_ast[0])
    
    node = JRuby.parse("1")
    new_ast = node.child_nodes[0].transform(nil)
    assert(Compiler::Duby::Fixnum === new_ast)
  end
  
  def test_fixnum
    node = JRuby.parse("1")
    
    new_ast = node.child_nodes[0].transform(nil)
    assert(Compiler::Duby::Fixnum === new_ast)
    assert_equal(1, new_ast.literal)
  end
  
  def test_float
    node = JRuby.parse("1.0")
    
    new_ast = node.child_nodes[0].transform(nil)
    assert(Compiler::Duby::Float === new_ast)
    assert_equal(1.0, new_ast.literal)
  end
end