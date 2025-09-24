/********************************************************************************/
/*                                                                              */
/*              BaitUserFile.java                                               */
/*                                                                              */
/*      Representation of a file user needs for testing                         */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2011 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved                                    *
 *                                                                               *
 * This program and the accompanying materials are made available under the      *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at                                                           *
 *      http://www.eclipse.org/legal/epl-v10.html                                *
 *                                                                               *
 ********************************************************************************/



package edu.brown.cs.limbabb.bait;

import java.io.File;

import edu.brown.cs.ivy.xml.IvyXmlWriter;

class BaitUserFile implements BaitConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private File            user_file;
private String          access_name;
private String          context_name;
private UserFileType    file_mode; 

private static int      file_counter = 0;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

BaitUserFile(File f,String unm,UserFileType ft)
{
   this();
   set(f,unm,ft);
}


BaitUserFile() 
{
   user_file = null;
   access_name = null;
   file_mode = UserFileType.READ;
   
   ++file_counter;
   context_name = "LimaUserFile_" + file_counter;
}



/********************************************************************************/
/*                                                                              */
/*      Setup Methods                                                           */
/*                                                                              */
/********************************************************************************/

void set(File local,String name,UserFileType ft)
{
   user_file = local;
   if (user_file != null && (name == null || name.length() == 0)) 
      name = user_file.getName();
   if (name.startsWith("/limba/")) {
      name = name.substring(4);
    }
   else if (name.startsWith("s:")) {
      name = name.substring(2);
    }
   access_name = name;
   file_mode = ft;
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

File getFile()                                  { return user_file; }
String getFileName() 
{
   if (user_file == null) return null;
   return user_file.getPath();
}

String getAccessName()                          { return access_name; }
String getJarName()                             { return context_name; }
UserFileType getFileMode()                      { return file_mode; }

boolean isValid()
{
   return user_file != null && access_name != null;
}


/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

void addEntry(IvyXmlWriter xw) 
{
   xw.begin("USERFILE");
   xw.field("NAME",access_name);
   xw.field("JARNAME",context_name);
   xw.field("ACCESS",file_mode);
   xw.end("USERFILE");
}



@Override public String toString()
{
   return access_name + " <= " + user_file.getPath() + " (" + file_mode + ")";
}


}       // end of class BaitUserFile




/* end of BaitUserFile.java */

