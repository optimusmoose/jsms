# JS-MS
*JavaScript Mass Spectrometry*, a visualization GUI for Mass Spectrometry signal data. Presents a robust, 3-dimensional graph view that allows the creation, editing, and saving of XIC and envelope annotations in addition to panning, rotating, and zooming actions. Interfaces via HTTP to the MsDataServer API for access to MS data stored in the MzTree format.

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

## Usage: Terminal/Command-line start
- `java -jar msDataServer/target/msDataServer-<version>.jar`
- Consider expanding the maximum memory available to the application.
  This can be done by passing the `-Xmx` flag. For example, add `-Xmx8g` to give
  the application a maximum 8 gigabytes of memory.

#### See *instruction.html* for instructions on iteracting with JS-MS
*instruction.html* is accessible by clicking the **?** button in the top-right of the graph view.

## Build (optional)
- Run `mvn package` from within the project root directory

## License
This work is published under the Gnu General Public License (GPL) v2. Please see the LICENSE file at the root level of this repository for more details.

## Notice
For commercial license opportunities, contact Dr. Rob Smith at robert.smith@mso.umt.edu.
