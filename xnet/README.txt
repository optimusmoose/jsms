## System requirements:
- Build-time: Java 8 JDK, Apache Maven
- Runtime: Java 8 JRE or higher

## Build procedure
- Run `mvn package` from within the project root directory

## Testing
- Place ground truth CSVs in root, Run TestRun with name of test file to cluster traces. Xnet will return a List of IsotopicEnvelope datatypes that can be used. 
