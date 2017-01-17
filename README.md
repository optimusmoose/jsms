# JS-MS
*JavaScript Mass Spectrometry*, a visualization GUI for Mass Spectrometry signal data. Presents a robust, 3-dimensional graph view that allows panning, rotating, and zooming actions. Interfaces via HTTP to the MsDataServer API for access to MS data stored in the MzTree format. 

## Dependencies
###JS-MS
* Modern Web Browser supporting:
	* HTML5
	* WebGL

###MsDataServer
* Java 8

## Usage
1. Run msDataServer-1.0.jar: Navigate to jar and double click OR run command: `java -jar msDataServer-1.0.jar`
    
2. Enter desired port number in the MsDataServer window
3. Click **Start Server**, JS-MS will open in your default browser and MsDataServer will be activated
4. Click **Open File**, a file chooser dialog will appear over the MsDataServer window
5. Select an *mzML* or *MzTree* file
6. Begin interacting with the graph view

#### See *instruction.html* for instructions on iteracting with JS-MS
*instruction.html* is accessible by clicking the **?** button in the top-right of the graph view.

## Build
JS-MS is a web application that does not require building. All files a served over the web to your browser via MsDataServer.

MsDataServer is managed and built via Maven. To build [download Maven](https://maven.apache.org/download.cgi) and [set up your Maven environment](https://www.tutorialspoint.com/maven/maven_environment_setup.htm). Then, with *pom.xml* visible, run the command `mvn install`.
Alternatively, you can build with NetBeans IDE. Download [NetBeans](https://netbeans.org/downloads/), open the *msDataServer* project and use the NetBeans build tool (located in the toolbar) to build.

## License
This work is published under the Gnu General Public License (GPL) v2. Please see the LICENSE file at the root level of this repository for more details.

## Notice 
For commercial license opportunities, contact Dr. Rob Smith at robert.smith@mso.umt.edu.
