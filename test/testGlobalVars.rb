require 'test/minirunit'

test_check "Test Global Vars"

$output = "a global variable."
def testGlobalVariable
    test_ok("a global variable." == $output)
end
testGlobalVariable

$bar = 5
alias $foo $bar
test_ok($foo == 5)

$bar = 10
test_ok($foo == 10)

$foo = 5
test_ok($bar == 5)
