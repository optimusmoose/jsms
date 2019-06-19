package edu.umt.ms.traceSeg;

import java.util.Optional;

/**
 * Provides default trace parameters by returning an empty Optional whenever possible
 */
public class DefaultTraceParametersProvider implements TraceParametersProvider {
    @Override
    public Optional<Boolean> getCentroided() {
        return Optional.empty();
    }
    
    @Override
    public Optional<Double> getProbabilityThreshold() {
        return Optional.empty();
    }

    @Override
    public Optional<Double> getMaxRTDist() {
        return Optional.empty();
    }

    @Override
    public Optional<Double> getIntFactor() {
        return Optional.empty();
    }

    @Override
    public Optional<Double> getIntLimit() {
        return Optional.empty();
    }

    @Override
    public Optional<Double> getRTScale() {
        return Optional.empty();
    }
}
