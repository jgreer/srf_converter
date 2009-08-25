import java.awt.*;
import java.awt.image.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.Arrays;

public class Png2Srf {
  int checksum = 0;
  int sectionCount = 0;
  int[] sectionWidths = new int[10];
  int[] sectionHeights = new int[10];
  BufferedImage rgbImage = null, maskImage = null;
  
  public static void main(String[] args) {
    try {
      boolean forceOverwrite = false;
      
      // Check for the "-f" command line arg.
      int curArg = 0;
      while (curArg < args.length && args[curArg].startsWith("-")) {
        if (args[curArg].indexOf("f") >= 0) forceOverwrite = true;
        curArg += 1;
      }
      
      if (curArg == (args.length - 2)) {
        Png2Srf p2s = new Png2Srf();
        p2s.convert(args[curArg], args[curArg + 1], forceOverwrite);
      } else {
        printUsage();
      }
      
    } catch (IOException e) {
      System.out.println("Error encountered!");
    }
  }
  
  public static void printUsage() {
    System.out.println("");
    System.out.println("Usage: java Png2Srf [options] <png_base> <srf_filename>");
    System.out.println("");
    System.out.println("Options:");
    System.out.println("  -f Force overwriting of existing file.");
    System.out.println("");
    System.out.println("Example: java Png2Srf vehicle newvehicle.srf");
    System.out.println("  Reads vehicle_info.txt, vehicle.png, (and");
    System.out.println("  possibly vehicle_mask.png), and outputs");
    System.out.println("  newvehicle.srf.");
    System.out.println("");
  }
  
  public void convert(String pngBase, String srfFilename, boolean forceOverwrite) throws IOException {
    int i;
    File file;
    
    // Be a little helpful if they added the .png extension.
    if (extName(pngBase).equalsIgnoreCase(".png")) {
      System.out.println("Note: stripping '.png' off the end of png_base.");
      pngBase = pngBase.substring(0,pngBase.length() - 4);
    }
    
    String[] filenames = { pngBase + ".png", pngBase + "_info.txt" };

    // Check if filenames exist...
    for (i = 0; i < filenames.length; i ++) {
      file = new File(filenames[i]);
      if (!file.exists()) {
        System.out.println("Error: Couldn't find file '" + filenames[i] + "'.");
        return;
      }
    }

    // Be a little helpful if they forgot the .srf extension.
    if (extName(srfFilename).equals("")) {
      System.out.println("Note: adding '.srf' to the end of srf_filename.");
      srfFilename += ".srf";
    }
    
    file = new File(srfFilename);
    if (!forceOverwrite && file.exists()) {
      System.out.println("Error: File '" + srfFilename + "' already exists.  Use the '-f' option to overwrite existing files.");
      return;
    }
        
    //
    // Read the pngbase_info.txt file...
    //
    String maskFilename = null;
    int fullImageWidth = 0;
    int fullImageHeight = 0;
    int sectionNum = 0;
    BufferedReader infoReader = new BufferedReader(new FileReader(new File(filenames[1])));
    try {
      String line,label,value;
      int colonPos;
      
      while ((line = infoReader.readLine()) != null) {
        if ((colonPos = line.indexOf(':')) > 0 && line.length() >= (colonPos + 2)) {
          label = line.substring(0, colonPos);
          value = line.substring(colonPos + 2,line.length());
          
          if (label.equals("MaskFile")) {
            if (!value.equals("<none>")) maskFilename = value;
          }
          if (label.equals("Width")) fullImageWidth = safeParseInt(value);
          if (label.equals("Height")) fullImageHeight = safeParseInt(value);
          if (label.equals("SectionCount")) sectionCount = safeParseInt(value);
          if (label.startsWith("SectionWidth") && label.length() == 13) {
            sectionNum = safeParseInt(label.substring(12,13));
            if (sectionNum > 0) sectionWidths[sectionNum - 1] = safeParseInt(value);
          }
          if (label.startsWith("SectionHeight") && label.length() == 14) {
            sectionNum = safeParseInt(label.substring(13,14));
            if (sectionNum > 0) sectionHeights[sectionNum - 1] = safeParseInt(value);
          }
        }
      }

    } finally {
      infoReader.close();
    }
    
    if (sectionCount == 0) {
      System.out.println("Error: Info file doesn't contain a valid section count.");
      return;
    }
    for (i = 0; i < sectionCount; i++) {
      if (sectionHeights[i] == 0 || sectionWidths[i] == 0) {
        System.out.println("Error: Info file doesn't contain valid dimensions for section " + (i+1) + ".");
        return;
      } else {
        System.out.println("Image Section Dimensions: " + sectionWidths[i] + "x" + sectionHeights[i]);
      }
    }
    
    int expectedImageWidth = 0;
    int expectedImageHeight = 0;
    for (i = 0; i < sectionCount; i++) {
      if (sectionWidths[i] > expectedImageWidth) expectedImageWidth = sectionWidths[i];
      expectedImageHeight += sectionHeights[i];
    }
    
    if (expectedImageWidth != fullImageWidth || expectedImageHeight != fullImageHeight) {
      System.out.println("Warning: Image dimensions in image file don't match up.");
    }

    if (maskFilename != null) {
      System.out.println("Converting PNGs to SRF with separate alpha mask.");
      rgbImage = ImageIO.read(new FileInputStream(filenames[0]));
      maskImage = ImageIO.read(new FileInputStream(maskFilename));
    } else {
      System.out.println("Converting PNG to SRF.");
      rgbImage = ImageIO.read(new FileInputStream(filenames[0]));
    }
    
    if (rgbImage.getWidth() < expectedImageWidth || rgbImage.getHeight() < expectedImageHeight) {
      System.out.println("PNG file is too small to contain all image sections.");
      return;
    }
    
    if (maskImage != null && maskImage.getWidth() < expectedImageWidth && maskImage.getHeight() < expectedImageHeight) {
      System.out.println("Mask file is too small to contain all image sections.");
      return;
    }
    
    FileOutputStream srf = new FileOutputStream(srfFilename);
    writeSRFHeader(srf);
    int curYPos = 0;
    for (i = 0; i < sectionCount; i++) {
      writeImageSection(srf, i, curYPos);
      curYPos += sectionHeights[i];
    }
    writeSRFFooter(srf);
    srf.close();
  }
  
  public void writeSRFHeader(FileOutputStream srf) throws IOException {
    srf.write("GARMIN BITMAP 01".getBytes());
    addBytesToChecksum("GARMIN BITMAP 01".getBytes());
    writeInt32(srf,4);
    writeInt32(srf,4);
    writeInt32(srf,sectionCount);
    writeInt32(srf,5);
    writePString(srf,"578");
    writeInt32(srf,6);
    writePString(srf,"1.00");
    writeInt32(srf,7);
    writePString(srf,"006-D0578-XX");
  }
  
  public void writeImageSection(FileOutputStream srf, int sectionNum, int yBase) throws IOException {
    int x,y,a,color;
    int w = sectionWidths[sectionNum];
    int h = sectionHeights[sectionNum];
    // Image section header.
    writeInt32(srf,0);
    writeInt32(srf,16);
    writeInt32(srf,0);
    writeInt16(srf,h);
    writeInt16(srf,w);
    writeInt16(srf,2064);
    writeInt16(srf, w * 2);
    writeInt32(srf,0);
    
    // Alpha Data
    writeInt32(srf,11);
    writeInt32(srf, w * h);
    for (y = 0; y < h; y++) {
      for (x = 0; x < w; x++) {
        if (maskImage != null) {
          a = maskImage.getRGB(x,y + yBase);
        } else {
          a = rgbImage.getRGB(x,y + yBase) >> 24;
        }
        a = encodeAlpha(a);
        srf.write(a);
        checksum += a;
      }
    }
    
    // RGB Data
    writeInt32(srf,1);
    writeInt32(srf, w * h * 2);
    for (y = 0; y < h; y++) {
      for (x = 0; x < w; x++) {
        color = rgbImage.getRGB(x,y + yBase) & 0xffffff;
        writeInt16(srf,encodeColor(color));
      }
    }
  }
  
  public void writeSRFFooter(FileOutputStream srf) throws IOException {
    long bytesWritten = srf.getChannel().position();
    long bytesToWrite = 255 - (bytesWritten % 256);
    for (long x = 0; x < bytesToWrite; x++) {
      srf.write(0xff);
      checksum += 0xff;
    }
    
    // Write out the checkbyte.
    srf.write((256 - (checksum & 255)) & 255);
  }

  public int safeParseInt(String s) {
    int i;
    try { i = Integer.parseInt(s); } catch (NumberFormatException e) { i = 0; }
    return i;
  }

  // Read a string from file -- one that's prefixed by a 32-bit int with its length.
  public String readPString(FileInputStream s) throws IOException {
    int len = readInt32(s);
    byte[] buffer = new byte[len+1];
    s.read(buffer, 0, len);
    buffer[len] = 0; // Terminate string with a zero byte.
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
