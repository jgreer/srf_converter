package net.techmods.srf_converter.lib;

import org.jruby.Ruby;
import org.jruby.RubyRuntimeAdapter;
import org.jruby.javasupport.JavaEmbedUtils;
import java.util.ArrayList;

// This technique for starting JRuby is taken from
// http://wiki.jruby.org/wiki/Direct_JRuby_Embedding
public class Main {
  public static void main(String[] args) {
    System.setProperty("com.apple.mrj.application.apple.menu.about.name", "SRF Converter");
    
    Ruby runtime = JavaEmbedUtils.initialize(new ArrayList());
    RubyRuntimeAdapter evaler = JavaEmbedUtils.newRuntimeAdapter();
    evaler.eval(runtime, "require 'net/techmods/srf_converter/lib/application_bootstrap'");
    JavaEmbedUtils.terminate(runtime);
  }
}
