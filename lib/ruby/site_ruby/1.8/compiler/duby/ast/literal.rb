module Compiler::Duby::AST
  class Array < Node
    def initialize(parent)
      super(parent, yield(self))
    end
  end
  
  class Fixnum < Node
    include Literal
    
    def initialize(parent, literal)
      super(parent)
      @literal = literal
    end
    
    def infer(typer)
      typer.fixnum_type
    end
  end
  
  class Float < Node
    include Literal
    
    def initialize(parent, literal)
      super(parent)
      @literal = literal
    end
    
    def infer(typer)
      typer.float_type
    end
  end
  
  class Hash < Node; end
  
  class String < Node
    include Literal
    
    def initialize(parent, literal)
      super(parent)
      @literal = literal
    end
    
    def infer(typer)
      @inferred_type ||= typer.string_type
    end
  end
  
  class Symbol < Node; end
end