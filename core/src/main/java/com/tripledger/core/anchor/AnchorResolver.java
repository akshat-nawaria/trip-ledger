package com.tripledger.core.anchor;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.model.Anchor;
import com.tripledger.core.model.TripEvidence;
import java.util.List;

// Each resolver looks at the trip and returns the points we're confident the cab went through.
public interface AnchorResolver {

    List<Anchor> resolve(TripEvidence evidence, BillingRules rules);
}
