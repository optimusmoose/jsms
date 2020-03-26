package edu.umt.ms.traceSeg;

import java.util.Optional;
import java.util.Scanner;

/**
 * Retrieves trace segmentation parameters from standard input.
 * Prompts the user for values, returning an empty Optional when invalid values are provided
 */
public class ConsoleTraceParametersProvider implements TraceParametersProvider {
    private Scanner inputScanner;

    public ConsoleTraceParametersProvider() {
        inputScanner = new Scanner(System.in);
    }

    private Optional<Double> getDouble(String label) {
        System.out.print("Input a value for " + label + ": ");
        String line = inputScanner.nextLine();
        try { return Optional.of(Double.parseDouble(line)); }
        catch (NumberFormatException ex) { return Optional.empty(); }
    }

    @Override
    public Optional<Boolean> getCentroided() {
        return getDouble("centroided (nonzero = centroid, 0 = profile").map(d -> Math.abs(d) > 0.000001);
    }

    @Override
    public Optional<Double> getProbabilityThreshold() {
        return getDouble("probability threshold");
    }

    @Override
    public Optional<Double> getMaxRTDist() {
        return getDouble("Maximum RT distance");
    }

    @Override
    public Optional<Double> getIntFactor() {
        return getDouble("Intensity factor");
    }

    @Override
    public Optional<Double> getIntLimit() {
        return getDouble("Intensity limit");
    }

    @Override
    public Optional<Double> getRTScale() {
        return getDouble("RT Scale");
    }
}
