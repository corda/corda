How to represent pointers to states in the type system? Opaque or exposed as hashes?

# Create states vs check states?

1. Derive output states entirely from input states + signed commands, *or*
2. Be given the output states and check they're valid

The advantage of 1 is that it feels safer: you can't forget to check something in the output state by accident. On
the other hand, then it's up to the platform to validate equality between the states (probably by serializing them
and comparing bit strings), and that would make unit testing harder as the generic machinery can't give good error
messages for a given mismatch. Also it means you can't do an equivalent of OP_RETURN and insert extra no-op states 
in the output list that are ignored by all the input contracts. Does that matter if extensibility/tagging is built in
more elegantly? Is it better to prevent this for the usual spam reasons?

The advantage of 2 is that it seems somehow more extensible: old contracts would ignore fields added to new states if
they didn't understand them (or is that a disadvantage?)

# What precisely is signed at each point?

