# JS-MS
*JavaScript Mass Spectrometry*, a visualization GUI for Mass Spectrometry signal data. Presents a robust, 3-dimensional graph view that allows the creation, editing, and saving of XIC and envelope annotations in addition to panning, rotating, and zooming actions. Interfaces via HTTP to the MsDataServer API for access to MS data stored in the MzTree format.

## Citations
If you are publishing any work related to the execution of this software, *please cite the following papers* which describe it:
> @article{rosen2017js,
  title={JS-MS: a cross-platform, modular javascript viewer for mass spectrometry signals},
  author={Rosen, Jebediah and Handy, Kyle and Gillan, Andr{\'e} and Smith, Rob},
  journal={BMC bioinformatics},
  volume={18},
  number={1},
  pages={469},
  year={2017},
  publisher={BioMed Central}
}

> @article{handy2017fast,
  title={Fast, axis-agnostic, dynamically summarized storage and retrieval for mass spectrometry data},
  author={Handy, Kyle and Rosen, Jebediah and Gillan, Andr{\'e} and Smith, Rob},
  journal={PloS one},
  volume={12},
  number={11},
  pages={e0188059},
  year={2017},
  publisher={Public Library of Science}
}

> @article{gutierrez2019xnet,
  title={XNet: A Bayesian approach to Extracted Ion Chromatogram Clustering for Precursor Mass Spectrometry Data},
  author={Gutierrez, Matthew and Handy, Kyle and Smith, Rob},
  journal={Journal of proteome research},
  year={2019},
  publisher={ACS Publications}
}

## Dependencies
* Run: Java 8 JRE or higher, WebGL (included in major browsers)
* Build (optional): Java 8 JDK, Apache Maven

## Usage: GUI
- Navigate to /msDataServer/target in a file manager
- Double click msDataServer-*version*.jar
- You may need to make the .jar file executable first. This procedure varies depending on your system configuration.
- A server window will open. You will now be able to open a mass spectrometry data file and start the HTTP server and launch JS-MS.
- The msDataServer jar file is self-contained can be run independently from the project folder as a standalone package
- Enter desired port number in the MsDataServer window
- Click **Start Server**, JS-MS will open in your default browser and MsDataServer will be activated
- Click **Open File**, a file chooser dialog will appear over the MsDataServer window
- Select an *mzML* or *MzTree* file
- Begin interacting with the graph view
- **NOTE**: JSMS' file storage and retrieval system incurs overhead costs that amortize well on files of reasonable or large size, but which causes notable delays even if loading small files. Please be patient! This is a one-time cost when building a tree with a new raw file. Subsequent file loads are very fast once an mzTree file is built.

## Usage: Terminal/Command-line start
- `java -jar msDataServer/target/msDataServer-<version>.jar`
- Consider expanding the maximum memory available to the application.
  This can be done by passing the `-Xmx` flag. For example, add `-Xmx8g` to give
  the application a maximum 8 gigabytes of memory.

#### See *instruction.html* for instructions on iteracting with JS-MS
*instruction.html* is accessible by clicking the **?** button in the top-right of the graph view.

## Build (optional)
- Run `mvn package` from within the project root directory

## Modules
- msDataServer: mzTree core and HTTP API. Includes a GUI with buttons to perform most actions provided by other modules
- tracesegmentation: creates segmentation data, extensible with different file format accessors
- xnet: trace clustering (traces -> envelopes) and data types used by mzTree storage
- correspondence: matching of finished envelope data from several files to highlight similarities and differences between data sets

## License
This work is published under the MIT license.
