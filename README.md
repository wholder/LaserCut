<p align="left"><img src="https://github.com/wholder/LaserCut/blob/master/images/LaserCut%20Logo.svg?sanitize=true"></p>
<p align="center"><img src="https://github.com/wholder/LaserCut/blob/master/images/LaserCut%20Screenshot.png" width="25%" height=25%"></p>
LaserCut is an experimental, "Swiss Army Knife" type of program for creating 2D designs by combining primitive shapes using constructive geometry and then sending them to a laser cutter for vector cutting or vector engraving.  It began from my frustration with Epilog™ over their lack of support for Mac OS X drivers for the Zing and having to purchase and constantly update both MS Windows™ and [Parallels™](https://www.parallels.com) just to use the Zing.  Then, one day, I discovered a wonderful Java library named [LibLaserCut](https://github.com/t-oster/LibLaserCut) (originally created by Thomas Oster for his [VisiCut](http://hci.rwth-aachen.de/visicut) program) and used it to begin coding my own solution.  After that, LaserCut slowly grew as I added more and more features.  This means that the feature set of LaserCut happens to be things that I needed it to do and not the result of some well-thought-out plan.  However, I tried to make the design as modular as possible.  For example, you can add new 2D shapes by subclassing the superclass`CADShape`and writing a few overloaded methods.  I'm making LaserCut available under the BSD license, [as described in by Wikipedia](https://en.wikipedia.org/wiki/BSD_licenses), so feel free to adapt and use my original code, as needed.

If you just want to try out the program, you don't need to download and compile the source code, as I try to maintain a pre-built, executable JAR file in the [out/artifacts/LaserCut_jar](https://github.com/wholder/LaserCut/tree/master/out/artifacts/LaserCut_jar) folder from which you can download and run LaserCut as long as you have Java installed on your system.  On a Mac, simply double click the LaserCut.jar file to run it once you've downloaded it, although you'll probably have to right click and select "Open" the  first time you run LaserCut due to the Mac OS X security check and the fact that I'm currently unable to digitally sign the JAR file.  You should also be able to double click and run using Windows, but some versions of Windows may require you to [enable this ability](https://www.addictivetips.com/windows-tips/run-a-jar-file-on-windows/) first.  LaserCut also seems to work fine on  Linux Mint 18.2 (KDE Edition) using Java 10.0.2, but I had to fiddle with permissions to get serial I/O working for the Mini Laser and Mini CNC features..
## Requirements
The current code and runnable Jar file requires Java 8, or later.
I wrote LaserCut on a Mac Pro using the _Community_ version of [IntelliJ IDEA from JetBrains](https://www.jetbrains.com/idea/) and OS X is the only environment where I have extensively tested and used LaserCut .  However, as the code is nearly 100% Java, it should also run on MS Windows and Linux systems.  One exception is JSSC (Java Simple Serial Connector), which contains low-level, native drivers, but JSSC is only needed to talk to GRBL-based laser cutters.  Feel free to report any issues you discover.  I'll do my best, when time permits, to investigate them, but I cannot guarantee fixes, timely or otherwise.
### Issue with JSSC 2.8.0 and Java 9, or later on 64 bit Windows 10
The issue with a JSSC's `jSSC-2.8_x86_64.dll` driver when using Java 9, or later on 64 bit Windows 10 seems to be resolved using Java 10.0.1+10 and Windows 10 Home, Version 1803, Build 17134.165.  As of 7/17/2018 I have successfully run LaserCut's MiniLaser feature using JSSC 2.8.0 (built in) to communicate with GRBL 1.1.
# Important Note
LaserCut uses the Java Language's reflection and object serialization features to store and load design files.  This means that future versions of LaserCut may introduce changes that make LaserCut unable to read design files saved by older versions.  I'm trying to code in a way that avoid this, but I can make no guarantees about future compatability with older saved files.
## Basic Features
- Create and place simple 2D vector shapes such as rectangles, rounded rectangles, ovals, circles, n-sided polygons and text outlines
- Create and place Reference Point objects which are display-only objects you can use as reference points or group with other shapes to provide a common origin point.
- Specify position and dimensions in inches (default), centimeters or millimeters
- Enable a grid placement for more precise positioning
- Create and place cutouts for various sizes of Nema Stepper Motors
- Group and ungroup shapes
- Click and drag to select a set of shapes
- Shift click and drag to add to set of selected shapes
- Click and drag to reposition shapes on the cutting surface in order to make the best use of your materials
- Move (translate) 2D shapes and groups of 2D shapes to precise positions using parametric input
- Rotate grouped shapes around the location of one of the grouped shapes
- Align the position of a shape, or a group of shapes to the position of another shape
- Edit shape parameters and set to either cut, or engrave the vector outline of the shape
- Uses Java's geom package to perform 2D, constructive geometry-like operations on shapes, such as:
  - Add/Merge one or more simple 2D shapes to another shape to create a new shape
  - Subtract/Remove one or more shapes from another shape to get a new shape
- Zoom and Pan support
- Generate Spur Gears of various sizes and parameters
- All shapes can be set to be cut or engraved by the laser cutter (Zing only)
- Send jobs to an Eplilog™ Zing™ over an Ethernet connection (USB not supported)
- Send jobs to a GRBL-based laser cutter with support for jogging position of laser head (experimental)
- Path Optimzation laser cuts nested shapes, such as holes, before the shapes they are nested inside
- Import vector outlines from an SVG files (beta)
- Import vector outlines from a DXF files (beta)
- Import drill holes and outlines from a Gerber file (experimental)
- Export designs to SVG vector files (beta)
- Export vector designs to PDF files (experimental)
- Export vector designs to DXF files
- Export vector designs to EPS files
- Freeform drawing of shapes (Beta) using [Catmull-Rom Splines](https://en.wikipedia.org/wiki/Centripetal_Catmull–Rom_spline) and then automatically convert them into [Bézier Curve](https://en.wikipedia.org/wiki/Bézier_curve)-based shapes.
- Raster images which can be engraved on Zing or used as tracing templates for spline shapes
## Jog Controls
<p align="center"><img src="https://github.com/wholder/LaserCut/blob/master/images/Jog%20Dialog.png"></p>

## New Additions
  - **7/6/2018** - Added a Material Settings selection menu for Zing Laser.  Material settings are stored as text file in the resource fork of the Jar file.  Currently only two materials, _1/8" Baltic Birch Plywood_ and _1/8" Cast Acrylic Plastic_ are supported.  More work needed to add a decent library of materials settings.
  - **7/7/2018** - Added new "Units" menu to select either `inches`, or `millimeters` as the default units for offset and size measurements.  Note: can switch back and forth, as needed, and can override setting by adding "`in`" or "`mm`" as a suffix to an input value.  Caution: still not completely tested.
  - **7/7/2018** - Enabled `java.awt.desktop`-related features, recompiled for Java 9 and updated to PDFBox 2.0.11.
  - **7/11/2018** - Added simple parallel path finder for eventual support for some 2D CNC operations.
  - **7/13/2018** - Reworked GRBL Settings dialog to support editing and saving settings and added "Info" button for each item.
  - **7/19/2018** - Added real time position readout to Jog controls and G-Code Monitor dialog and G-Code Monitor now stays visible until all steps are processed.
  - **7/20/2018** - Moved "Materials" Menu into "Zing Settings" Menu using enhancements to ParameterDialog class.
  - **7/22/2018** - Added "Get GRBL Coordinates" Menu to Mini CNC (used to read and set G54, etc coordinate space values)
  - **4/23/2019** - Extensive rewrite to implement new click and drag to select set feature
  - **5/7/2019** - Added "Export to DXF" using the [JDXF](https://jsevy.com/wordpress/index.php/java-and-android/jdxf-java-dxf-library/) library by Jonathan Sevy and fixed some errors in "Import DXF" when reading files with SPLINE entities.
  - **5/10/2019** - Added "Export to EPS" using new EPSWriter class.
  - **5/12/2019** - JavaCut now runs under Java 8, or greater (Java 10-only code commented out)
  - **5/21/2019** - Added PathPlanner class to optimize laser cutting paths (still in development)
## Under Development
- Support for a user-extendable library of specialized cutout shapes, such as for mounting RC Servos (to replace and extend the current Nema Stepper code)
- Import vector shapes from EPS (Encapsulated PostScript®) files
- Get a certificate so I can sign the JAR file (costs $$$, sigh)
## Basic Operations
I'm working on more comprehensive documentation to be built into the code but, in the meantime, here are some basic operations you can do:
- Select a shape from the "**Shapes**" menu, fill in the parameters, as needed, press "**Place**" and then click the mouse point where you want the shape to be located.  Note: select "centered" for the origin of the shape to be its center, otherwise it will the the upper left point.
- Click the outline of a shape to select it (shape outline turns blue) and display the origin as a small (+)
- Click and drag the (+) origin to reposition a shape
- Click and drag around a set of shapes to select them
- Click with Shift down to add, or remove a shape from a selected set
- With one shape selected, press and hold down the shift key while you click on the outline of another shape to group the two objects together (both outlines show as blue).  Shift click again to ungroup a shape.
- Click one shape in a group to display its origin and then reposition the whole group by clicking and dragging its (+) origin.
- Select a shape and select **Edit->Edit Selected** (or press the **CMD-E** shortcut key) to bring up the shape parameter dialog.
- Select a shape and select **Edit->Move Selected** (or press the **CMD-M** shortcut key) to reposition a shape, or a group of shapes.
- Select a shape and select **Edit->Delete Selected** (or press the **CMD-X** shortcut key) to delete a shape, or a group of shapes.
- Select a shape and select **Edit->Duplicate Selected** (or press the **CMD-D** shortcut key) to create a duplicate shape you can then reposition.
- Double Click with Meta Down to zoom in on location clicked, Shift-Double Click with Meta Down to zoom out.
- Spline Shapes
  - Select `Spline Curve` from the `Shapes` menu then click to place the origin of the shape (does not create a point on the curve)
  - Click again to place first control point then again to place another (repeat to trace out curve.)
  - Click on first control point placed to complete and close curve.
  - Click and drag on an already placed control point to move it.
  - Click and drag on the origin to move the entire spline shape
## Credits
LaserCut uses the following Java code to perform some of its functions:
- [LibLaserCut](https://github.com/t-oster/LibLaserCut) (used to control the Zing Laser)
- [Java Simple Serial Connector 2.8.0](https://github.com/scream3r/java-simple-serial-connector) "JSSC" (used to communicate with GRBL-based laser cutters)
- [Apache PDFBox® 2.0.11](https://pdfbox.apache.org) (used by the "Export PDF" feature)
- [Apache commons-logging 1.2](https://commons.apache.org/proper/commons-logging/) (needed by Apache PDFBox)
- [Gear Shapes based on "Java Gear Generator: Involute and Fillet"](http://printobjects.me/catalogue/ujava-gear-generator-involute-and-fillet_520801/) by brush701
- [JDXF](https://jsevy.com/wordpress/index.php/java-and-android/jdxf-java-dxf-library/) by Jonathan Sevy (used to implement the "Export DXF" feature)
- [IntelliJ IDEA from JetBrains](https://www.jetbrains.com/idea/) (my favorite development environment for Java coding. Thanks JetBrains!)
