<p align="center">
  <img src="https://github.com/wholder/LaserCut/images/LaserCut Screenshot.png">
</p>
## LaserCut
LaserCut is an experimental, "swiss army knife" type of program for creating 2D designs using primitive shapes and constructive geometry and then sending them to a laser cutter to be cut.  It started out of my frustration with Epilog over their lack of support for Mac OS X and then slowly grew as I added features.  Here's is a short list of what LaserCut can do:

- Create and place simple 2D shapes such as rectangles, rounded rectangles, ovals, circles, n-sided polygons and text outlines
- Create and place Reference Point objects which are display-only oblects you can use as reference points or group with other shapes to provide a common origin point.
- Specify position and dimensions in inches (default) or millimeters
- Enable a grid placement for more precise positioning
- Create and place cutouts for various sizes of Nema Stepper Motors
- Group and ungroup shapes (shift and click)
- Move (translate) 2D shapes and groups of 2D shapes
- Rotate grouped shapes around the location of one of the grouped shapes
- Align the position of a shape, or a group of shapes to the position of another shape
- Add one or more simple 2D shapes to another shape to create a new shape
- Subtract one or more shapes from another shape to get a new shape
- Zoom and pan support
- Generate Spur Gears of various sizes and parameters
- All shapes can be set to be cut or engraved by the laser cutter (Zing only)
- Send jobs to an Eplilog Zing over an Ethernet connection (USB not supported)
- Send jobs to a GRBL-based laser cutter (experimental)
- Import vector outlines from SVG files (experimental)
- Import drill holes and outlines from a Geber file (experimental)
- Export to designs to a PDF file (experimental)

## Documentation
I'm working on more comprehensive documentation to be built into the code but, in the meantime, here are some basics:
- Select a shape from the "Shapes" menu, fill in the parameters, as needed, press "Place" and then click the mouse point where you want the shape to be located.  Note: select "centered" for the origin of the shape to be its center, otherwise it will the the upper left point.
- Click the outline of a shape to select it (turns blue) and display the origin as a small (+)
- Click and drag the (+) origin to reposition a shape
- With one shape selected, press and hold down the shift key while you click on the outline of another shape to group the two objects together (both show as blue).  Shift click again to ungroup a shape.
- Click one shape in a group to display its origin and then reposition the whole group by clicking and dragging its (+) origin.
- Select a shape and select Edit->Edit Selected (or press the CMD-E shortcut key) to bring up the shape parameter dialog.
- Select a shape and select Edit->Move Selected (or press the CMD-M shortcut key) to reposition a shape, or a group of shapes.
- Select a shape and select Edit->Delete Selected (or press the CMD-X shortcut key) to delete a shape, or a group of shapes.
- Select a shape and select Edit->Duplicate Selected (or press the CMD-D shortcut key) to create a duplicate shape you can then reposition.

## Dependencies
LaserCut uses the following Java code to perform some of its functions:
- Apache PDFBox 2.0.7 (used by the "Export PDF" feature)
- Java Simple Serial Connector "JSSC" (used to commuicate with GRBL-based laser cutters)
- LibLaserCutter (used to drive Zing Laser Cutter)
- Apache commons-logging 1.2 (used by Apache PDFBox 2.0.7)
