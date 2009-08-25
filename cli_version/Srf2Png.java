import java.awt.*;
import java.awt.image.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.Arrays;

public class Srf2Png {
  int checksum = 0;
  int sectionCount = 0;
  int[] sectionWidths = new int[10];
  int[] sectionHeights = new int[10];
  BufferedImage rgbImage = null, maskImage = null;
  
  public static void main(String[] args) {
    try {
      boolean separateMask = false;
      boolean forceOverwrite = false;
      
      // Check for "-m" command line arg.
      int curArg = 0;
      while (curArg < args.length && args[curArg].startsWith("-")) {
        if (args[curArg].indexOf("m") >= 0) separateMask = true;
        if (args[curArg].indexOf("f") >= 0) forceOverwrite = true;
        curArg += 1;
      }

      if (curArg == (args.length - 2)) {
        Srf2Png s2p = new Srf2Png();
        s2p.convert(args[curArg], args[curArg + 1], separateMask, forceOverwrite);
      } else {
        printUsage();
      }
      
    } catch (IOException e) {
      System.out.println("Error encountered!");
    }
  }
  
  public static void printUsage() {
    System.out.println("");
    System.out.println("Usage: java Srf2Png [options] <srf_filename> <png_base>");
    System.out.println("");
    System.out.println("Options:");
    System.out.println("  -m Use a separate image for the alpha masks.");
    System.out.println("  -f Force overwriting of existing files.");
    System.out.println("");
    System.out.println("Example: java Srf2Png -m vehicle.srf newvehicle");
    System.out.println("  Reads vehicle.srf and creates newvehicle.png,");
    System.out.println("  newvehicle_mask.png, and newvehicle_info.txt.");
    System.out.println("");
  }
  
  public void convert(String srfFilename, String pngBase, boolean separateMask, boolean forceOverwrite) throws IOException {
    int i;
    File file;
    
    // Be a little helpful if they added the .png extension.
    if (extName(pngBase).equalsIgnoreCase(".png")) {
      System.out.println("Note: stripping '.png' off the end of png_base.");
      pngBase = pngBase.substring(0,pngBase.length() - 4);
    }

    // Be a little helpful if they forgot the .srf extension.
    if (extName(srfFilename).equals("") && !((new File(srfFilename)).exists()) && ((new File(srfFilename + ".srf")).exists())) {
      System.out.println("Note: adding '.srf' to the end of srf_filename.");
      srfFilename += ".srf";
    } else if (!(new File(srfFilename)).exists()) {
      System.out.println("Error: Couldn't find SRF file '" + srfFilename + "'.");
      return;
    }
    
    String[] filenames = { pngBase + ".png", pngBase + "_mask.png", pngBase + "_info.txt" };

    if (separateMask) {
      System.out.println("Converting SRF to PNG with separate alpha mask.");
    } else {
      System.out.println("Converting SRF to PNG.");
    }
    
    // Check if filenames exist already...
    if (!forceOverwrite) {
      for (i = 0; i < filenames.length; i ++) {
        file = new File(filenames[i]);
        if (file.exists()) {
          System.out.println("Error: File '" + filenames[i] + "' already exists.  Use the '-f' option to overwrite existing files.");
          return;
        }
      }
    }
    
    FileInputStream srf = new FileInputStream(srfFilename);
    
    //
    // Header Section
    //
    
    String fileIdentifier = readBasicString(srf,16); // "GARMIN BITMAP 01"
    if (!fileIdentifier.equals("GARMIN BITMAP 01")) {
      System.out.println("Invalid SRF file.");
      return;
    }
    srf.skip(8); // 4,4 -- purpose unknown
    sectionCount = readInt32(srf);
    srf.skip(4); // 5 -- purpose unknown
    srf.skip(7); // P"578"
    srf.skip(4); // 6 -- purpose unknown
    String versionString = readPString(srf);
    srf.skip(4); // 7 -- purpose unknown
    String productString = readPString(srf); // Product Code
    System.out.println("SRF Revision:   " + versionString);
    System.out.println("SRF Product:    " + productString);
    System.out.println("Image Sections: " + sectionCount);
    
    // Make sure we don't have too many image sections.
    if (sectionCount > 9) {
      System.out.println("\nError: Too many image sections in this SRF.");
      return;
    }
    
    //
    // Get size info of the srf and final PNG image(s)
    // 
    int fullImageWidth = 0;
    int fullImageHeight = 0;
    readSectionSizes(srf);
    for (i = 0; i < sectionCount; i++) {
      if (sectionWidths[i] > fullImageWidth) fullImageWidth = sectionWidths[i];
      fullImageHeight += sectionHeights[i];
    }
    
    //
    // Read each image section into the PNG(s)
    //
    
    int curYPos = 0;

    if (separateMask) {
      rgbImage =  new BufferedImage(fullImageWidth, fullImageHeight, BufferedImage.TYPE_INT_RGB);
      maskImage = new BufferedImage(fullImageWidth, fullImageHeight, BufferedImage.TYPE_BYTE_GRAY);
    } else {
      rgbImage =  new BufferedImage(fullImageWidth, fullImageHeight, BufferedImage.TYPE_INT_ARGB);
    }
    
    for (i = 0; i < sectionCount; i++) {
      readImageSection(srf, curYPos, separateMask);
      curYPos += sectionHeights[i];
    }

    srf.close();
    

    //
    // Now create the PNG(s)
    //
    
    file = new File(filenames[0]);
    ImageIO.write(rgbImage, "png", file);
    if (separateMask) {
      file = new File(filenames[1]);
      ImageIO.write(maskImage, "png", file);
    }
    
    //
    // And lastly, write the info file
    //
    
    file = new File(filenames[2]);
    PrintWriter infoWriter = new PrintWriter(new FileWriter(file));
    try {
      infoWriter.println("MaskFile: " + (separateMask ? filenames[1] : "<none>"));
      infoWriter.println("Width: " + fullImageWidth);
      infoWriter.println("Height: " + fullImageHeight);
      infoWriter.println("SectionCount: " + sectionCount);
      for (i = 0; i < sectionCount; i++) {
        infoWriter.println("SectionWidth" + (i+1) + ": " + sectionWidths[i]);
        infoWriter.println("SectionHeight" + (i+1) + ": " + sectionHeights[i]);
      }
    } finally {
      infoWriter.close();
    }
  }
  
  public void readSectionSizes(FileInputStream srf) throws IOException {
    // Save our spot so we can come back later.
    long initialPosition = srf.getChannel().position();
    
    int i;
    for (i = 0; i < sectionCount; i++) {
      srf.skip(12);
      int h = readInt16(srf);
      int w = readInt16(srf);
      sectionHeights[i] = h;
      sectionWidths[i] = w;
      srf.skip(8 + 8 + w*h + 8 + w*h*2);
    }
    
    // Restore stream position.
    srf.getChannel().position(initialPosition);
  }
  
  public void readImageSection(FileInputStream srf, int yBase, boolean separateMask) throws IOException {
    // First, the image header
    srf.skip(12); // 0,16,0 -- purpose unknown
    int height = readInt16(srf);
    int width = readInt16(srf);
    srf.skip(2); // 16,8 -- purpose unknown
    int linebytes = readInt16(srf);
    srf.skip(4); // 0 -- purpose unknown
    System.out.println("Image Section Dimensions: " + width + "x" + height);
    
    // Next, the alpha data
    srf.skip(4); // 11 -- possibly the data type?
    srf.skip(4); // length of following data, equals width*height
    byte[] alphaBuffer = new byte[width*height];
    srf.read(alphaBuffer, 0, width*height);
    
    // Finally, the RGB data...
    srf.skip(4); // 1 -- possibly the data type?
    srf.skip(4); // length of following data, equals width*height*2
    byte[] rgbBuffer = new byte[width*height*2];
    srf.read(rgbBuffer, 0, width*height*2);
    
    int x,y,a,color,pos;
    
    // Write the data into our in-memory images.
    pos = 0;
    for (y = 0; y < height; y++) {
      for (x = 0; x < width; x++) {
        a = decodeAlpha(alphaBuffer[pos]);
        color = decodeColor(rgbBuffer[pos*2], rgbBuffer[pos*2+1]);
        if (separateMask) {
          rgbImage.setRGB(x, yBase + y, color);
          maskImage.setRGB(x, yBase + y, (a << 16) + (a << 8) + a);
        } else {
          rgbImage.setRGB(x, yBase + y, (a << 24) + color);
        }
        pos += 1;
      }
    }
  }
  
  // Read a string from file -- one that's prefixed by a 32-bit int with its length.
  public String readPString(FileInputStream s) throws IOException {
    int len = readInt32(s);
    return readBasicString(s, len);
  }
  
  public String readBasicString(FileInputStream s, int len) throws IOException {
    byte[] buffer = new byte[len];
    s.read(buffer, 0, len);
    return new String(buffer);
  }

  // Read a little-endian short from the file.
  public int readInt16(FileInputStream s) throws IOException {
    int i = 0;
    i = s.read() + (s.read() << 8);
    return i;
  }

  // Read a little-endian int from the file.
  public int readInt32(FileInputStream s) throws IOException {
    int i = 0;
    i = s.read() + (s.read() << 8) + (s.read() << 16) + (s.read() << 24);
    return i;
  }

  public void addBytesToChecksum(byte[] bytes) {
    int len = Arrays.asList(bytes).size();
    for (int i = 0; i < len; i++) {
      checksum += bytes[i];
    }
  }

  // Write a string to file -- one that's prefixed by a 32-bit int with its length.
  public void writePString(FileOutputStream s, String string) throws IOException {
    writeInt32(s, string.length());
    byte[] bytes = string.getBytes();
    s.write(bytes);
    addBytesToChecksum(bytes);
  }

  // Write a little-endian int to the file.
  public void writeInt32(FileOutputStream s, int i) throws IOException {
    for (int j = 0; j < 4; j++) {
      s.write(i & 255);
      checksum += (i & 255);
      i >>= 8;
    }
  }

  // Write a little-endian short to the file.
  public void writeInt16(FileOutputStream s, int i) throws IOException {
    for (int j = 0; j < 2; j++) {
      s.write(i & 255);
      checksum += (i & 255);
      i >>= 8;
    }
  }
  
  // Turn a 16-bit color into a 24-bit one.
  public int decodeColor(byte b1, byte b2) {
    int r,g,b;
    int v = ((b2 & 0xff) << 8) + (b1 & 0xff);
    r = (v & 0xf800) << 8;
    g = (v & 0x07c0) << 5;
    b = (v & 0x001f) << 3;
    return r + g + b;
  }

  // Turn a 16-bit color into a 24-bit one.
  public int encodeColor(int c) {
    int r,g,b;
    r = (c & 0xff0000) >> 19;
    g = (c & 0x00ff00) >> 11;
    b = (c & 0x0000ff) >> 3;
    return (r << 11) + (g << 6) + b;
  }
  
  // Convert a 7-bit inverted alpha value to 8-bit standard.
  public int decodeAlpha(byte b) {
    int a = (b & 255) << 1;
    if (a >= 254) a = 255;
    return 255 - a;
  }

  // Convert a 7-bit inverted alpha value to 8-bit standard.
  public byte encodeAlpha(int a) {
    a = (255 - (a & 255)) >> 1;
    if (a == 127) return (byte)128;
    return (byte)a;
  }
  
  // Get the extension part from a filename string.
  public String extName(String filename) {
    int lastDot = filename.lastIndexOf('.');
    if (lastDot < 0) return "";
    return filename.substring(lastDot, filename.length());
  }
}
