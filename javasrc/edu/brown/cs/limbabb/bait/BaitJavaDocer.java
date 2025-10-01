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

class BaitJavaDocer implements BaitConstants, BaitConstants.ResponseHandler
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private BumpLocation bump_location;
private String prior_javadoc;
private String method_contents;
private int prior_start;
private int prior_end;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

BaitJavaDocer(BumpLocation loc)
{
   bump_location = loc;
   prior_javadoc = null;
   method_contents = null;
   prior_start = -1;
   prior_end = -1;
   scanMethod();
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
      if (prior_javadoc != null && !prior_javadoc.isEmpty()) {
         xw.cdataElement("PRIOR",prior_javadoc);
       }
      xw.cdataElement("CODE",method_contents);
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
   CommandArgs args = new CommandArgs("TYPE",what,"USECONTEXT",true);
   BaitFactory bf = BaitFactory.getFactory();
   bf.issueXmlCommand("FINDJDOC",args,body,this);
}


@Override public void handleResponse(Element rslt)
{
   String jdoc = IvyXml.getTextElement(rslt,"JDOC"); 
   if (jdoc != null && !jdoc.isEmpty()) {
      replaceJavaDoc(jdoc);
    }
}


/********************************************************************************/
/*                                                                              */
/*      Get the method body and prior JavaDoc                                   */
/*                                                                              */
/********************************************************************************/

private void scanMethod()
{
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
      else if (Character.isLetterOrDigit(ch)) {
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
   method_contents = code.substring(x1);
   IvyLog.logD("LIMBA","Found prior " + prior_start + " " + prior_end);
}


/********************************************************************************/
/*                                                                              */
/*      Insert javadoc                                                          */
/*                                                                              */
/********************************************************************************/

void replaceJavaDoc(String jdoc)
{
   // might need to get bump location again and then scan it again
   File f = bump_location.getFile();
   String proj = bump_location.getProject();
   BaleConstants.BaleFileOverview bfo = BaleFactory.getFactory().
         getFileOverview(proj,f);
   int start = bump_location.getDefinitionOffset();
   
   try {
      if (prior_start < 0 || prior_end < 0) {
         bfo.insertString(start,jdoc,null);
       }
      else {
         bfo.replace(prior_start,prior_end-prior_start,jdoc,false,false);
       }
    }
   catch (BadLocationException e) {
      BoardLog.logE("BAIT","Problem inserting javadoc",e);
    }
}


}       // end of class BaitJavaDocer




/* end of BaitJavaDocer.java */

