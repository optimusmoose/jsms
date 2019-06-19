/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.storage;

/**
 * Factory pattern implementation for determining storage implementation at runtime
 * @author Kyle
 */
public class StorageFacadeFactory
{
    // Facade options
    public static enum Facades { Hybrid };
    
    /**
     * Constructs the StorageFacade implementation corresponding to the facade choice
     * @param choice Facade choice
     * @return Corresponding StorageFacade implementation
     */
    public static StorageFacade create(Facades choice){
        switch(choice){
            default:
            case Hybrid:
                return new HybridStorage();
        }
    }
}
