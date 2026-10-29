package com.tripledger.core.cleaning;

import com.tripledger.core.model.Ping;
import java.util.List;

/**
 * One cleaning step. Each filter does exactly one job, which makes them easy to test
 * one by one and easy to explain when someone asks "why did this ping disappear?".
 *
 * Rule for implementations: don't touch the list you get, return a new one.
 */
public interface PingFilter {

    // shows up in the cleaning report and logs, so keep it short and don't rename it casually
    String name();

    List<Ping> apply(List<Ping> pings);
}
