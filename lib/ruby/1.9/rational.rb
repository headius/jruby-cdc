class Fixnum

  alias quof fdiv
  alias rdiv quo

  alias power! ** unless defined?(0.power!)
  alias rpower **

end

class Bignum

  alias quof fdiv
  alias rdiv quo

  alias power! ** unless defined?(0.power!)
  alias rpower **

end
