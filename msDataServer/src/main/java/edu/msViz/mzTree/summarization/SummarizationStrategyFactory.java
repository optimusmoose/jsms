/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.summarization;

/**
 * Factory pattern for creating the selected SummarizationStrategy
 * @author Kyle
 */
public class SummarizationStrategyFactory
{   
    public static enum Strategy { IntensityCutoff, UniformStriding, WeightedStriding, UniformSampling, WeightedSampling, WeightedReservoirSampling };
    
    public static SummarizationStrategy create(Strategy strategy){
        switch(strategy){
            default:
            case IntensityCutoff:
                return new IntensityCutoffStrategy();
            case UniformStriding:
                return new UniformStridingStrategy();
            case WeightedStriding:
                return new WeightedStridingStrategy();
            case UniformSampling:
                return new UniformSamplingStrategy();
            case WeightedSampling:
                return new WeightedSamplingStrategy();
            case WeightedReservoirSampling:
                return new WeightedReservoirSampling();
        }
    }
}
