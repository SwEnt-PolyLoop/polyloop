<!-- Made with Claude. -->
# PolyLoop

The language of PolyLoop, a peer-to-peer rental app for EPFL students. `docs/ARCHITECTURE.md` and `docs/SCREENS.md` hold the rules; this file only fixes the words. It covers the deposit negotiation and disputes for now, and will be filled along the way, a term at a time once its meaning is agreed.

## Language

### Rentals

**Deposit negotiation**:
How the lender and the borrower agree on a rental's deposit before the request is accepted: the lender makes an offer, the borrower accepts it, declines it or makes one counteroffer, and the lender accepts or declines that. Only the deposit is negotiated; the rental price is fixed.
_Avoid_: Settlement (that is the first stage of a dispute)

**Offer**:
A deposit amount waiting for an answer during the deposit negotiation: the lender's offer, which comes with the pickup and drop-off addresses, or the borrower's counteroffer.
_Avoid_: Proposal (that is a split in a dispute), bid

### Disputes

**Dispute**:
A disagreement between the lender and the borrower about how to split a rental's deposit, opened when the lender flags damage after the return. At most one per rental.
_Avoid_: Claim, conflict, case

**Split**:
How a deposit is shared between the lender and the borrower, as the lender's whole percentage; the borrower gets the rest.
_Avoid_: Ratio, division

**Split proposal**:
A split offered by one party to the other during direct settlement, which the other can accept or counter.
_Avoid_: Bid

**Direct settlement**:
The first stage of a dispute, where the two parties exchange split proposals.
_Avoid_: Negotiation (that is the deposit negotiation before acceptance)

**LLM proposal**:
The split, with its reasoning, that the LLM gives from the condition photos once a dispute leaves direct settlement; both parties accept or reject it. Also the name of that stage.
_Avoid_: Gemini proposal, AI proposal, recommendation, verdict

**Court**:
The last stage of a dispute, reached when a party rejects the LLM proposal, where a court ruling is uploaded and reviewed by an admin.
_Avoid_: Arbitration
