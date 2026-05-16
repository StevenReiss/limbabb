/********************************************************************************/
/*                                                                              */
/*              BaitJavaDocer.java                                              */
/*                                                                              */
/*      Handle JavaDoc generation command                                       */
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
import java.util.LinkedList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;

import org.w3c.dom.Element;

import edu.brown.cs.bubbles.bale.BaleConstants;
import edu.brown.cs.bubbles.bale.BaleFactory;
import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.bump.BumpClient;
import edu.brown.cs.bubbles.bump.BumpLocation;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class BaitJavaDocer implements BaitConstants, BaitConstants.ResponseHandler
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private BumpLocation bump_location;
private ItemScanner item_scan;
private String method_types;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

BaitJavaDocer(BumpLocation loc,String types)
{
   bump_location = loc;
   method_types = types;
   item_scan = new ItemScanner(loc);
}


/********************************************************************************/
/*                                                                              */
/*      Generate and insert the javadoc                                         */
/*                                                                              */
/********************************************************************************/

void process()
{
   String body = null;
   try (IvyXmlWriter xw = new IvyXmlWriter()) {
      xw.begin("JAVADOC");
      if (item_scan.getJavadoc() != null && !item_scan.getJavadoc().isEmpty()) {
         xw.cdataElement("PRIOR",item_scan.getJavadoc());
       }
      xw.cdataElement("CODE",item_scan.getContents());
      xw.end("JAVADOC");
      body = xw.toString();
    }
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
      case STATIC_INITIALIZER :
         what = "METHOD";
         break;
      case FIELD :
      case ENUM_CONSTANT :
         what = "FIELD";
         break;
    }
   CommandArgs args = new CommandArgs("WHAT",what,
         "USECONTEXT",true,
         "NAME",bump_location.getSymbolName());
   if (method_types != null) args.put("TYPES",method_types);
   BaitFactory bf = BaitFactory.getFactory();
   bf.issueXmlCommand("FINDJDOC",args,body,this);
}


@Override public void handleResponse(Element rslt)
{
   List<Element> todo = new LinkedList<>();
   for (Element jelt : IvyXml.children(rslt,"JDOC")) {
      todo.add(0,jelt);
    }
   if (todo.isEmpty()) return;
   
   SwingUtilities.invokeLater(new JavaDocInserter(todo));
}


/********************************************************************************/
/*                                                                              */
/*      Insert javadoc                                                          */
/*                                                                              */
/********************************************************************************/

void replaceJavaDoc(String jdoc,ItemScanner scan)
{
   if (!jdoc.endsWith("\n")) jdoc += "\n";
   // might need to get bump location again and then scan it again
   File f = bump_location.getFile();
   String proj = bump_location.getProject();
   BaleConstants.BaleFileOverview bfo = BaleFactory.getFactory().
         getFileOverview(proj,f);
   int start = scan.getStartOffset();
   
   try {
      if (scan.getPriorStart() < 0 || scan.getPriorEnd() < 0) {
         bfo.insertString(start,jdoc,null);
       }
      else {
         bfo.replace(scan.getPriorStart(),
               scan.getPriorEnd()-scan.getPriorStart(),
               jdoc,false,false);
       }
    }
   catch (BadLocationException e) {
      BoardLog.logE("BAIT","Problem inserting javadoc",e);
    }
}


private class JavaDocInserter implements Runnable {
   
   private List<Element> work_list;
   
   JavaDocInserter(List<Element> todo) {
      work_list = todo;
    }
   
   @Override public void run() {
      if (method_types == null) {
         // handle single method and single javadoc
         if (work_list.size() == 1) {
            Element rslt = work_list.get(0);
            String jdoc = IvyXml.getTextElement(rslt,"JDOC"); 
            if (jdoc != null && !jdoc.isEmpty()) {
               replaceJavaDoc(jdoc,item_scan);
             }
          }
       }
      else if (method_types.equals("*")) {
         // handle single class
         if (work_list.size() == 1) {
            Element rslt = work_list.get(0);
            String jdoc = IvyXml.getTextElement(rslt,"JDOC"); 
            if (jdoc != null && !jdoc.isEmpty()) {
               replaceJavaDoc(jdoc,item_scan);
             }
          }
       }
      else {
         BumpClient bc = BumpClient.getBump();
         for (Element jelt : work_list) {
            String name = IvyXml.getAttrString(jelt,"NAME");
            String jdoc = IvyXml.getText(jelt);
            if (jdoc == null || jdoc.isEmpty()) continue;
            List<BumpLocation> locs = bc.findMethod(bump_location.getProject(),
                  name,false);
            if (locs.isEmpty()) {
               int idx0 = name.indexOf("(");
               if (idx0 < 0) idx0 = name.length();
               int idx1 = name.lastIndexOf(".",idx0);
               if (idx1 > 0) {
                  String name1 = name.substring(idx1+1);
                  locs = bc.findMethod(bump_location.getProject(),name1,false);
                }
             }
            for (BumpLocation loc : locs) {
               if (!loc.getFile().equals(bump_location.getFile())) continue;
               ItemScanner lscn = new ItemScanner(loc);
               // check for location already done?
               replaceJavaDoc(jdoc,lscn);
               break;
             }
          }
         BoardLog.logD("BAIT","Handle multiple method javadoc");
       }
    }
}



/********************************************************************************/
/*                                                                              */
/*      Get the method body and prior JavaDoc                                   */
/*                                                                              */
/********************************************************************************/


private static final class ItemScanner {
   
   private BumpLocation bump_location;
   private String prior_javadoc;
   private String item_contents;
   private int prior_start;
   private int prior_end;
   
   
   ItemScanner(BumpLocation loc) {
      bump_location = loc;
      prior_javadoc = null;
      item_contents = null;
      prior_start = -1;
      prior_end = -1;
      scanMethod();
    }
   
   String getJavadoc()                          { return prior_javadoc; }
   int getPriorStart()                          { return prior_start; }
   int getPriorEnd()                            { return prior_end; }
   String getContents()                         { return item_contents; }
   int getStartOffset() {
      return bump_location.getDefinitionOffset();
    }
   
   private void scanMethod() {
      File f = bump_location.getFile();
      String proj = bump_location.getProject();
      BaleConstants.BaleFileOverview bfo = BaleFactory.getFactory().
            getFileOverview(proj,f);
      String code = null;
      try {
         code = bfo.getText(bump_location.getDefinitionOffset(),
               bump_location.getDefinitionEndOffset() - bump_location.getDefinitionOffset());
       }
      catch (BadLocationException e) {
         BoardLog.logE("BAIT","Problem getting text for method",e);
         return;
       }
      
      int x1 = -1; 
      StringBuffer buf = new StringBuffer();
      StringBuffer jdoc = null;
      boolean inlinecmmt = false;
      boolean inareacmmt = false;
      char lastchar = 0;
      int havetext = 0;
      
      for (int i = 0; i < code.length(); ++i) {
         char ch = code.charAt(i);
         if (inlinecmmt) { 			// check for end of comment
            if (ch == '\n') {
               inlinecmmt = false;
             }
          }
         else if (inareacmmt) {
            if (jdoc != null) {
               jdoc.append(ch);
               if (Character.isLetterOrDigit(ch)) ++havetext;
             }
            if (lastchar == '*' && ch == '/') {
               inareacmmt = false;
               if (jdoc != null) {
                  if (havetext > 10) {
                     buf.append(jdoc);
                     buf.append("\n");
                     prior_end = bump_location.getDefinitionOffset() + i + 1;
                   }
                  else if (buf.isEmpty()) {
                     prior_start = -1;
                   }
                  jdoc = null;
                  havetext = 0;
                }
             }
          }
         
         if (inareacmmt || inlinecmmt) ;
         else if (Character.isLetterOrDigit(ch) || ch == '@') {
            if (x1 < 0) x1 = i;
            break;
          }
         else if (lastchar == '/') {
            if (ch == '/') {
               inlinecmmt = true;
             }
            else if (ch == '*') {
               if (i+1 < code.length() && code.charAt(i+1) == '*') {
                  havetext = 0;
                  if (prior_start < 0) {
                     prior_start = bump_location.getDefinitionOffset() + i - 1;
                   }
                  jdoc = new StringBuffer();
                  jdoc.append("/*");
                }
               inareacmmt = true;
             }
          }
         lastchar = ch;
       }
      if (!buf.isEmpty()) prior_javadoc = buf.toString();
      if (x1 < 0) x1 = 0;
      item_contents = code.substring(x1);
      IvyLog.logD("LIMBA","Found prior " + prior_start + " " + prior_end);
    }
   
}       // end of inner class ItemScanner


}       // end of class BaitJavaDocer




/* end of BaitJavaDocer.java */

