<p align="left"><img src="https://github.com/wholder/LaserCut/blob/master/images/LaserCut%20Logo.svg?sanitize=true" width="35%" height=35%"></p>
<p align="center"><img src="https://github.com/wholder/LaserCut/blob/master/images/LaserCut%20Screenshot.png"></p>
LaserCut is an experimental, "Swiss Army Knife" type of program for creating 2D designs by combining primitive shapes using constructive geometry and then sending them to a laser cutter for vector cutting or vector engraving.  It began from my frustration with Epilog™ over their lack of support for Mac OS X drivers for the Zing™ and having to purchase and constantly update both MS Windows™ and [Parallels™](https://www.parallels.com) just to use the Zing.  Then, one day, I discovered a wonderful Java library named [LibLaserCut](https://github.com/t-oster/LibLaserCut) (originally created by Thomas Oster for his [VisiCut](http://hci.rwth-aachen.de/visicut) program) and used it to begin coding my own solution.  After that, LaserCut slowly grew as I added more and more features.  This means that the feature set of LaserCut happens to be things that I needed it to do and not the result of some well-thought-out plan.  However, I tried to make the design as modular as possible.  For example, you can add new 2D shapes by subclassing the superclass`CADShape`and writing a few overloaded methods.  I'm making LaserCut available under the BSD license, [as described in by Wikipedia](https://en.wikipedia.org/wiki/BSD_licenses), so feel free to adapt and use my original code, as needed.<br/><br/>

If you just want to try out the program, you don't need to download and compile the source code, as I try to maintain a pre-built, executable JAR file in the [out/artifacts/LaserCut_jar](https://github.com/wholder/LaserCut/tree/master/out/artifacts/LaserCut_jar) folder from which you can download and run LaserCut as long as you have Java installed on your system.  On a Mac, simply double click the LaserCut.jar file to run it once you've downloaded it, although you'll probably have to right click and select "Open" the  first time you run LaserCut due to the Mac OS X security check and the fact that I'm currently unable to digitally sign the JAR file.  You should also be able to double click and run using Windows, but some versions of Windows may require you to [enable this ability](https://www.addictivetips.com/windows-tips/run-a-jar-file-on-windows/) first.  LaserCut also seems to work fine on  Linux Mint 18.2 (KDE Edition) using Java 10.0.2, but I had to fiddle with permissions to get serial I/O working for the Mini Laser and Mini CNC features.

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
- All shapes can be set to be cut or engraved by the laser cutter
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
- Raster images which can be engraved on Zing or used as tracing templates for spline shapes.  Also supports full rotation of raster images as well as control over DPI of of engraved images.

## Selecting the Output Device
To select the current output device, choose "Preferences" in the "File" menu then select the device and the press "Save".  LaserCut will remember this selection the next time the program is launched.
<p align="center"><img src="https://github.com/wholder/LaserCut/blob/master/images/Preferences%20Dialog.png"></p>

Currently supported Output Devices devices include:
 - Epilog™ Zing (requires Ethernet connection, as USB is not supprted)
 - Mini Laser (small, Chinese-made laser engraver updated to run GRBL 1.1)
 - Micro Laser (small engraver built from DVD/CD stepper assembiles and updated to run GRBL 1.1)
 - Mini CNC (small, Chinese-made CNC Mill updated to run GRBL 1.1)
 - Silhouette™ Craft Cutters (tested with Cameo™ 3 and Curio™ models, but should work with others)
 - Cricut™ Mini Craft Cutter (running [Matt Williams' TeensyCNC firmware](https://github.com/seishuku/TeensyCNC))
 
## Jog Controls
GRBL-based devices, such as the Mini Laser also have a Jog control panel for moving the laser head around and selecting the origin.
<p align="center"><img src="https://github.com/wholder/LaserCut/blob/master/images/Jog%20Dialog.png"></p>

## Custom Device Settings
You can use the Output Device's Settings menu to configure speed and power settings, as well as other options.  For example, this is the Settings menu for the Mini Laser:
<p align="center"><img src="https://github.com/wholder/LaserCut/blob/master/images/Mini%20Laser%20Settings.png"></p>
The function of the different fields is, as follows:

 - **Use Path Planner** - if enabled, LaserCut tries to optimize the order of cuts so that interior paths, such as cutouts, or holes are cut before enclosing paths.  This help prevent miscutting caused by the work items shifting around on the cutting bed.
 - **Guide Beam Power** - If non zero, the laser is turned on at this setting whenever you ue the Jog menu to move the cutting head.  Note: for safety, use the lowest setting that displays a visible beam (**Warning** do not use if the cutting laser's beam wavelength is not visible, such as with Infrared Lasers, as this creates an invisible eye safety hazard) 
 - **Dynamic Laser** - This a feature of GRBL 1.1.  If enabled, it will automatically adjust laser power based on the current speed relative to the programmed rate.  Note: enabled by default when engraving.
 - **Cut Power** - Sets the laser power used for shapes set to be cut (vs engraved)
 - **Cut Speed** - Sets the feed rate (in inches/minute) used for shapes set to be cut (vs engraved)
 - **Engrave Power** - Sets the laser power used for shapes set to be engraved (vs cut)
 - **Engrave Speed** - Sets the feed rate (in inches/minute) used for shapes set to be engraved (vs cut)
 - **Engrave DPI** - Sets the raster (in dots/inch) use when raster engraving an image
 - **Workspace Zoom** - Used to set the default zoom factor used the workspace canvas (initial default is 1 : 1 for the Mini Laser)
 - **Workspace Width** - Used to set width of the workspace canvas (initial default is 7 inches for the Mini Laser)
 - **Workspace Height** - Used to set width of the workspace canvas (initial default is 8 inches for the Mini Laser)

## New Additions
  - **7/6/2018** - Added a Material Settings selection menu for Zing™ Laser.  Material settings are stored as text file in the resource fork of the Jar file.  Currently only two materials, _1/8" Baltic Birch Plywood_ and _1/8" Cast Acrylic Plastic_ are supported.  More work needed to add a decent library of materials settings.
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
  - **5/31/2019** - MiniLaser now supports engraving raster images (with optional rotation)
  - **6/4/2019** - Extensive rewrite of GRBL code and how serial ports are managed.  Now, only one output device can be active at one time, whicih prevents conflicts due to many USB serial interfaces, especially those based on cloned chipsets, showing identical USB device names.
  - **6/9/2019** - Added support for Silhouette™ craft cutters (currently tested with Curio and Cameo 3)
  - **6/18/2019** - Can now use mouse to resize and rotate Text and enforce resizing Resizable shapes to a minimum size
  - **6/27/2019** - Added experimental new code to support a Cricut™ Mini Modified to run [TeensyCNC](https://github.com/seishuku/TeensyCNC) by Matt Williams
  
## Under Development
- Support for a user-extendable library of specialized cutout shapes, such as for mounting RC Servos (to replace and extend the current Nema Stepper code)
- Better "Materials" menu and support for all Output Devices, not just the Zing
- Import vector shapes from EPS (Encapsulated PostScript®) files
- Get a certificate so I can sign the JAR file (costs $$$, sigh)

## Basic Operations
I'm working on more comprehensive documentation to be built into the code but, in the meantime, here are some basic operations you can do:
- Select a shape from the "**Shapes**" menu, fill in the parameters, as needed, press "**Place**" and then click the mouse point where you want the shape to be located.  Note: select "centered" for the origin of the shape to be its center, otherwise it will the the upper left point.
- Click the outline of a shape to select it (shape outline turns blue) and display the origin as a small (+)
- Select a shape and select **Edit->Edit Selected** (or press the **CMD-E** shortcut key) to bring up the shape parameter dialog
- Select a shape and select **Edit->Move Selected** (or press the **CMD-M** shortcut key) to reposition a shape, or a group of shapes
- Select a shape and select **Edit->Delete Selected** (or press the **CMD-X** shortcut key) to delete a shape, or a group of shapes
- Select a shape and select **Edit->Duplicate Selected** (or press the **CMD-D** shortcut key) to create a duplicate shape you can then reposition.
- Click and drag the (+) origin on a selected shape to reposition a shape
- With the Shift up (not pressed), click and drag the small square in the lower right corner of a selected shape's bounding box to resize the shape.
- With the Shift down (pressed), click and drag the small square, or circle in the lower right corner of a selected shape's bounding box to rotate the shape about the (+) origin point.
- Click on empty space then hold down the mouse button and drag a bounding box around a set of shapes to select them (purple color)
- Or, hold down Shift and click another shape to add it to the set of selected shapes
- Or, hold down Shift and click one of the selected shapes to remove it from the selected set
- With two, or more shapes selected (purple color), choose **Edit->Group Selected** to add these shapes to a group.  Note: grouped shapes (blue color) can still be individually selected to edit paramaters, but moving any shape will move the entire group
- To ungroup grouped shapes, select the group (blue color when selected) the choose **Edit->Ungroup Selected**
- Double Click with Meta Down to zoom in on the location clicked, Shift-Double Click with Meta Down to zoom out.

## Constructive Geometry on Grouped Shapes
- Grouped shapes can be combined into new shapes by using constructive geometry.  For example, create two, overlapping rectangles and group them.  The, with the group selected (blue color) choose **Edit->Add Grouped Shape(s) to Selected Shape** to add other grouped shapes to the selected shape (the one showing the (+) move control).  For example:
<p align="center"><img src="https://github.com/wholder/LaserCut/blob/master/images/Add.png"></p>

- Or, choose **Edit->Subtract Grouped Shape(s) to Selected Shape** to subtract the othe grouped shapes from the selected shape.  For example:
<p align="center"><img src="https://github.com/wholder/LaserCut/blob/master/images/Subtract.png"></p>

Note: you may need to experiment with these options to get the hang of how they work, but they can be used to create very complex shape outlines.

## Drawing a Spline Shape
 - Select `Spline Curve` from the `Shapes` menu then click to place the origin of the shape (does not create a point on the curve)
 - Click again to place first control point then again to place another (repeat to trace out curve.)
 - Click on first control point placed to complete and close curve.
 - Click and drag on an already placed control point to move it.
 - Click and drag on the origin to move the entire spline shape
  
## Raster Images
 - Choose **Shapes->Raster Image** to import an image file (jpeg, png, gif, or bmp) and then place it onto the canvas.  Some devices can engrave raster images and they can also be used as a reference for drawing shapes.  Note: be careful to make sure the imporated image is scaled to fit within the bounds of the canvas.  The DPI (dots/inch) used for engraving can be set independently from the imported image's DPI and it's scaled size on the canvas. Note: on slower computers, large images may draw sugglishly when placing, repositioning, or scaling the image using mouse controls.
 
## Credits
LaserCut uses the following Java code to perform some of its functions:
- [LibLaserCut](https://github.com/t-oster/LibLaserCut) (used to control the Zing Laser)
- [Java Simple Serial Connector 2.8.0](https://github.com/scream3r/java-simple-serial-connector) "JSSC" (used to communicate with GRBL-based laser cutters)
- [Apache PDFBox® 2.0.11](https://pdfbox.apache.org) (used by the "Export PDF" feature)
- [Apache commons-logging 1.2](https://commons.apache.org/proper/commons-logging/) (needed by Apache PDFBox)
- [Gear Shapes based on "Java Gear Generator: Involute and Fillet"](http://printobjects.me/catalogue/ujava-gear-generator-involute-and-fillet_520801/) by brush701
- [JDXF](https://jsevy.com/wordpress/index.php/java-and-android/jdxf-java-dxf-library/) by Jonathan Sevy (used to implement the "Export DXF" feature)
- [IntelliJ IDEA from JetBrains](https://www.jetbrains.com/idea/) (my favorite development environment for Java coding. Thanks JetBrains!)
