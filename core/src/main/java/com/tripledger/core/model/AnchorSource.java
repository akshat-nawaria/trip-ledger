package com.tripledger.core.model;

// where an anchor's position came from - shown in the breakdown so nobody has to guess
public enum AnchorSource {
    ACTUAL,          // what the driver app reported, and it looked fine
    PROFILE_HOME,    // ad-hoc rider, actual was unusable, so: home from profile + sign-in time
    PLANNED,         // planned rider, actual was unusable, so: planned location + planned time
    PLANNED_OFFICE   // trip end/start was too far from the office (or missing), so: the office itself
}
