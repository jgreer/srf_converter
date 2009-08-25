include Java
$LOAD_PATH << "lib"                              # running from filesystem
$LOAD_PATH << "net/techmods/srf_converter/lib"   # running from jar
$CLASSPATH << "vendor/swingx-0.9.2.jar"          # running from filesystem

require "view_builder"
require "srf_converter"

import javax.swing.UIManager
import java.lang.System

System.setProperty("apple.laf.useScreenMenuBar", "true");
UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName) rescue nil

srf_converter = SrfConverter.new(ViewBuilder.new)
srf_converter.run
