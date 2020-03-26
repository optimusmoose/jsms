package edu.umt.ms.traceSeg;

import java.util.Optional;


// TODO: find a way to tune these parameters based on the input data

/**
 * Provides methods to get trace segmentation tuning parameters
 * Methods returning an Optional type can return an empty value (NOT null!)
 * to indicate that a default should be used.
 */
public interface TraceParametersProvider {
    Optional<Boolean> getCentroided();
    Optional<Double> getProbabilityThreshold();
    Optional<Double> getMaxRTDist();
    Optional<Double> getIntFactor();
    Optional<Double> getIntLimit();
    Optional<Double> getRTScale();
}
