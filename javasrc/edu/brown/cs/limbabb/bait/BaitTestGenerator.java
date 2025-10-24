/********************************************************************************/
/*                                                                              */
/*              BaitTestGenerator.java                                          */
/*                                                                              */
/*      Handle user request to create test cases                                */
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

import javax.swing.text.BadLocationException;

import org.w3c.dom.Element;

import edu.brown.cs.bubbles.bale.BaleConstants;
import edu.brown.cs.bubbles.bale.BaleFactory;
import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.bump.BumpLocation;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class BaitTestGenerator implements BaitConstants, BaitConstants.ResponseHandler
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private BumpLocation    bump_location;
private String          context_contents;
private String          insert_class;
private boolean         new_class;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

BaitTestGenerator(BumpLocation loc,String incls,boolean newcls)
{
   bump_location = loc;
   insert_class = incls;
   new_class = newcls;
   context_contents = null;
   getContents();
}


/********************************************************************************/
/*                                                                              */
/*      Generate and insert the test cases                                      */
/*                                                                              */
/********************************************************************************/

void process()
{
   String body = null;
   try (IvyXmlWriter xw = new IvyXmlWriter()) {
      xw.begin("TOTEST");
      xw.field("TARGETCLASS",insert_class);
      xw.field("NEWCLASS",new_class);
      String what = "METHOD";
      switch (bump_location.getSymbolType()) {
         case ANNOTATION :
         case CLASS :
         case ENUM :
         case INTERFACE :
         case THROWABLE :
            what = "CLASS";
            break;
         case CONSTRUCTOR :
         case FUNCTION :
            what = "METHOD";
            break;
       }
      xw.field("SOURCETYPE",what);
      xw.field("SOURCEFILE",bump_location.getFile());
      xw.cdataElement("CODE",context_contents);
      xw.end("TOTEST");
      body = xw.toString();
    }
   
   CommandArgs args = new CommandArgs("USECONTEXT",true);
   BaitFactory bf = BaitFactory.getFactory();
   bf.issueXmlCommand("TESTS",args,body,this);
}


@Override public void handleResponse(Element rslt)
{
   IvyLog.logD("BAIT","Received tests: " + 
         IvyXml.convertXmlToString(rslt));
}

/********************************************************************************/
/*                                                                              */
/*      Get the contents to pass on                                             */
/*                                                                              */
/********************************************************************************/

private void getContents()
{
   File f = bump_location.getFile();
   String proj = bump_location.getProject();
   BaleConstants.BaleFileOverview bfo = BaleFactory.getFactory().
         getFileOverview(proj,f);
   context_contents = null;
   try {
      context_contents = bfo.getText(bump_location.getDefinitionOffset(),
	    bump_location.getDefinitionEndOffset() - bump_location.getDefinitionOffset());
    }
   catch (BadLocationException e) {
      BoardLog.logE("BAIT","Problem getting text for method",e);
    }
}


}       // end of class BaitTestGenerator




/* end of BaitTestGenerator.java */

