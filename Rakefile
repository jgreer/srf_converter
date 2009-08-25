require "rake/clean"

JRUBY_VERSION = "1.1.2"

def jruby
  "vendor/jruby-#{JRUBY_VERSION}/bin/jruby"
end

def jrubyc
  "vendor/jruby-#{JRUBY_VERSION}/bin/jrubyc"
end

task :default => "dist:build"

desc "Run the application from the filesystem"
task :run do
  sh "#{jruby} lib/application_bootstrap.rb"
end

namespace :java do
  output_directory = "classes"

  desc "Compile java executable stub class"
  task :stub => :clean do 
    mkdir_p output_directory
    sh %+javac -target 1.5 -d #{output_directory} -classpath vendor/jruby-complete-#{JRUBY_VERSION}.jar lib/Main.java+
  end

  desc "Compile the Ruby files into class files"
  task :rb => :clean do
    prefix = "net/techmods/srf_converter"
    sh %+#{jruby} #{jrubyc} -p #{prefix} -t #{output_directory} lib+
  end

  desc "Compile all source files into class files"
  task :compile => ["java:stub", "java:rb"]

  CLEAN.include output_directory
end

namespace :dist do
  desc "Build the deliverable jar"
  task :build => [:clean, 'java:compile'] do
    build_cmd = "ant dist"
    if PLATFORM =~ /mswin/
      sh(build_cmd  + '&') # makes windows wait until build is done
    else
      sh build_cmd 
    end
  end
  
  desc "Run the application from the deliverable jar"
  task :run => 'dist:build' do
    system "java -jar pkg/SrfConverter.jar"
  end

  CLEAN.include "pkg"
end
