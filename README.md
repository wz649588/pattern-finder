# pattern-finder
Extract change dependency graphs from Java code changes

## Requirements
This repository contains several Eclipse Plugin projects. The program can run normally under the following conditions:
* JDK 7
* Eclipse for RCP and RAP Developers, Version: Luna Release (4.4.0)
* Copying a Jar file to plugins/ folder of EClipse.

## Installation
Users need to import the projects into Eclipse. Note that these projects should be imported as Eclipse Plugin projects.

## Downloading Input Files
Users can download the program's input files from Google Drive. The files includes the input Java source code commits, the element list, the project libraries.

## Configuration
Users need to download necessary input files for the program, and save the files in the file system. 
Before running the program, users need to do some configurations in the code.
### Path of the Java source code commits
In the ConsoleGSydit project, the locations for configurations for input source code changes are in the Application.java in the consolegsydit package.
* Aries: line 62
* Cassandra: line 65
* Derby: line 68
* Mahout: line 71

Please modify the above code to match the paths of the according files saved in your computer.

### Path of Element List
In the ConsoleGSydit, the code location for configuration for input element list is in line 75 of the CommitComparatorClient.java file in the edu.vt.cs.changes package.

### Path of Java Library
The library files in this path will be used for analysis purpose. Users can set it in the line 76 of the CommitComparatorClient.java file in the edu.vt.cs.changes package.

### Path of Project Library
The project library in this path will also be used for analysis purpose. Users can set it in the line 77 of the CommitComparatorClient.java file in the edu.vt.cs.changes package.

## Running
Users should run the program in Eclipse, since the projects are Eclipse Plugin projects. The repository includes multiple projects, and users should run the ConsoleGSydit project as an Eclipse Application.
