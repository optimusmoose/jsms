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

#### See *instruction.html* for instructions on interacting with JS-MS
*instruction.html* is accessible by clicking the **?** button in the top-right of the graph view.

#### Creating ground truth data
- *Isotopic Trace Mode.* When a user enters isotopic trace mode, they are given the option to create a new trace or select an existing trace to edit. Each time a new trace is created, the trace is given an ID and color. Users select the points belonging to the trace by clicking and dragging a rectangle over the desired points to highlight them in the given color. The same procedure is used to edit an existing trace, only the control key is depressed while drawing the rectangle.

- *Isotopic Envelope Mode.* After the user has identified isotopic traces, they can group them together with isotopic envelope mode. Similar to isotopic trace mode, this mode creates a new envelope ID and color for each new envelope created. The user then selects all isotopic traces that belong to the same group. Isotopic traces can be grouped by clicking each trace or simply by dragging a line across all traces in an envelope. To help the user distinguish which isotopic traces belong together, the ruler tool shows m/z intervals corresponding to specific charge states. The ruler will appear wherever the mouse is placed when users select a number from the keypad. The ruler moves with the graph as the user zooms or pans, and will remain present until the user hits the tilde key. The m/z distance displayed is 1/z, where z is the number selected and the charge state of a hypothetical compound at the given mass. Users can also toggle between 2-D and 3-D mode while in either isotopic trace or isotopic envelope mode to ensure peak alignment. Isotopic traces can be added to existing isotopic envelopes at any time following this procedure and they can be removed in the same way while depressing the control key.

- *Mark as Noise Button.* When all distinguishable points in the current view have been annotated the user can mark all other points in the view as noise. When a point is marked as noise it will be colored gray in the view and given an ID of -1 when exported to .csv. To prevent users from marking unseen points as noise, the graph view must be displaying a number of points below the point threshold to ensure that the user is viewing every point within the (m/z, RT) coordinates and none are hidden.

#### Exporting ground truth data
- In the server window (where you start the server and launch the browser), you can export segmented data to a .csv that will feature all points in the file in the form: m/z, RT, intensity, isotopic trace ID, isotopic envelope ID. Isotopic IDs are unique, meaning they are not dependent on the isotopic envelope ID. A trace/envelope ID of zero means that point has not yet been assigned to a trace/envelope, and an ID of -1 means it has been assigned to noise using the "mark as noise" button.

#### Creating and editing bookmarks
- Bookmarks can be created in the program by first displaying the bookmarks bar by clicking on the bookmarks button in the lefthand bar. Next, you enter a label, m/z value, and RT value in the respective fields on the list. Existing entries can be edited by clicking the edit button beside the entry, or deleted by clicking the delete button beside the entry.

- To create a bookmark list outside of JS-MS, create a .tsv file that contains, for each line/bookmark, a text label, a float value for m/z, and a float value for RT. In JS-MS, you can open this bookmark list using the open button in the bookmark list. You can also export a list using the export button in the bookmarks list view.

- NOTE: Re-opening a bookmark list in JS-MS will overwrite any changes you have made to the list. Exporting a bookmark list will export the current version of the list as displayed. This is important to know if you edit or remove entries to the bookmark list while in JS-MS.

#### Inspecting data using bookmarks
- In the righthand panel, locate the "Jump To" radio button and ensure "Next Bookmark" is selected. Create or load a bookmark list as described above. Click on the bookmarks button in the lefthand bar to display the bookmark list. You can now navigate to a specific bookmark by clicking on the name of the bookmark, or the next bookmark in visited order by clicking the jump button (right arrow) on the lefthand bar.

#### Inspecting data using "Jump to Window"
- In the righthand panel, locate the "Jump to Window" area. Enter the m/z and RT window you would like to graph, and click the check button. The graph will respond by plotting the described area.

## Build (optional)
- Run `mvn package` from within the project root directory

## Modules
- msDataServer: mzTree core and HTTP API. Includes a GUI with buttons to perform most actions provided by other modules
- tracesegmentation: creates segmentation data, extensible with different file format accessors
- xnet: trace clustering (traces -> envelopes) and data types used by mzTree storage
- correspondence: matching of finished envelope data from several files to highlight similarities and differences between data sets

## License
This work is published under the MIT license.
