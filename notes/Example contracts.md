# Simple payment

CashState:
- Issuing institution
- Deposit reference (pointer into internal ledger)
- Currency code
- Claim size (initial state = size of original deposit)
- Public key of current owner

ExitCashState:
- Amount to reduce claim size by
- Signature signed by ownerPubKey

State transition function (contract):
1. If input states contains an ExitCashState, set reduceByAmount=state.amount
1. For all proposed output states, they must all be instances of CashState
   For all proposed input states, they must all be instances of CashState
2. Sum claim sizes in all predecessor states. Sum claim sizes in all successor states
3. Accept if outputSum == inputSum - reduceByAmount
