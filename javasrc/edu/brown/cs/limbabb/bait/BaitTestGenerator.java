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
import java.util.List;

import javax.swing.text.BadLocationException;

import org.w3c.dom.Element;

import edu.brown.cs.bubbles.bale.BaleConstants;
import edu.brown.cs.bubbles.bale.BaleFactory;
import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.bueno.BuenoFactory;
import edu.brown.cs.bubbles.bueno.BuenoLocation;
import edu.brown.cs.bubbles.bueno.BuenoProperties;
import edu.brown.cs.bubbles.bueno.BuenoConstants.BuenoKey;
import edu.brown.cs.bubbles.bueno.BuenoConstants.BuenoType;
import edu.brown.cs.bubbles.bump.BumpClient;
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
private BumpLocation    target_location;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

BaitTestGenerator(BumpLocation loc,String incls)
{
   bump_location = loc;
   target_location = null;
   insert_class = incls;
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
   String proj = bump_location.getProject();
   BumpClient bc = BumpClient.getBump();
   List<BumpLocation> clocs = bc.findTypes(proj,insert_class);
   if (clocs != null) {
      for (BumpLocation bl : clocs) {
         String nm = bl.getSymbolName();
         if (nm.equals(insert_class)) {
            target_location = bl;
            break;
          }
       }
    }
   
   String body = null;
   try (IvyXmlWriter xw = new IvyXmlWriter()) {
      xw.begin("TOTEST");
      xw.field("TARGETCLASS",insert_class);
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
      if (target_location != null) {
         xw.field("TARGETFILE",target_location.getFile());
       }
      else {
         xw.field("NEW",true);
       }
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
   
   if (target_location == null) {
      // new class to create
      BuenoFactory bf = BuenoFactory.getFactory();
      
      BuenoLocation cloc = bf.createLocation(bump_location.getProject(),
            insert_class,null,false);
      BuenoProperties bp = loadFileProperties(rslt);
//    BuenoProperties bp = new BuenoProperties();
//    bp.put(BuenoKey.KEY_NAME,insert_class);
//    String ctxt = IvyXml.getTextElement(rslt,"TESTCODE");
//    bp.put(BuenoKey.KEY_FULLTEXT,ctxt);
      bf.createNew(BuenoType.NEW_CLASS,cloc,bp);
    }
   else {
      // insert into existing class
      for (Element impelt : IvyXml.children(rslt,"IMPORT")) {
         addImport(impelt);
       }
      for (Element dclelt : IvyXml.children(rslt,"DECL")) {
         addDeclaration(dclelt);
       }
    }
}



private BuenoProperties loadFileProperties(Element xml)
{
   BuenoProperties bp = new BuenoProperties();
   
// String ctxt = IvyXml.getTextElement(xml,"TESTCODE");
// bp.put(BuenoKey.KEY_FULLTEXT,ctxt);
   
   int idx = insert_class.lastIndexOf(".");
   String pnm = insert_class.substring(0,idx);
   String cnm = insert_class.substring(idx+1);
   bp.put(BuenoKey.KEY_PACKAGE,pnm);
   bp.put(BuenoKey.KEY_NAME,cnm);
   
   Element topxml = IvyXml.getChild(xml,"TOP");
   bp.put(BuenoKey.KEY_MODIFIERS,IvyXml.getAttrInt(topxml,"MODINT"));
   String sup = IvyXml.getTextElement(xml,"SUPERCLASS");
   if (sup != null) {
      bp.addToArrayProperty(BuenoKey.KEY_EXTENDS,sup);
    }
   for (String s : IvyXml.getTextElements(xml,"IMPLEMENTS")) {
      bp.addToArrayProperty(BuenoKey.KEY_IMPLEMENTS,s);
    }
   String jdoc = IvyXml.getTextElement(topxml,"JAVADOC");
   if (jdoc != null) {
      bp.put(BuenoKey.KEY_COMMENT,jdoc);
      bp.put(BuenoKey.KEY_ADD_JAVADOC,true);
    }
   bp.put(BuenoKey.KEY_CONTENTS,IvyXml.getTextElement(topxml,"CONTENTS"));
   
   return bp;
}


private void addImport(Element impelt) 
{
   String typ = IvyXml.getText(impelt);
   boolean isstatic = IvyXml.getAttrBool(impelt,"STATIC");
   if (isstatic) typ = "static " + typ;
   BumpClient bd = BumpClient.getBump();
   Element edits = bd.fixImports(target_location.getProject(),
         target_location.getFile(),null,0,0,typ);
   if (edits != null) {
      BaleFactory.getFactory().applyEdits(target_location.getFile(),edits);
    }
}


private void addDeclaration(Element dclelt)
{
   BuenoProperties bp = new BuenoProperties();
   String nm = IvyXml.getAttrString(dclelt,"NAME");
   bp.put(BuenoKey.KEY_NAME,nm);
   BuenoFactory bf = BuenoFactory.getFactory();
   BuenoLocation cloc = bf.createLocation(bump_location.getProject(),
         insert_class,null,true);
   String ctxt = IvyXml.getTextElement(dclelt,"RAWCODE");
   bp.put(BuenoKey.KEY_FULLTEXT,ctxt);
   if (IvyXml.getAttrBool(dclelt,"FIELD")) {
      bf.createNew(BuenoType.NEW_FIELD,cloc,bp);
    }
   else if (IvyXml.getAttrBool(dclelt,"INNERTYPE")) {
      bf.createNew(BuenoType.NEW_INNER_CLASS,cloc,bp);
    }
   else {
      bf.createNew(BuenoType.NEW_METHOD,cloc,bp);
    }
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

