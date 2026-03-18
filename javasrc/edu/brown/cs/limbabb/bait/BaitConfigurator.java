/********************************************************************************/
/*                                                                              */
/*              BaitConfigurator.java                                           */
/*                                                                              */
/*      description of class                                                    */
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

import org.w3c.dom.Element;

import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.bubbles.buda.BudaBubbleArea;
import edu.brown.cs.bubbles.buda.BudaConstants;
import edu.brown.cs.ivy.xml.IvyXml;

class BaitConfigurator implements BaitConstants, BudaConstants.BubbleConfigurator
{


/********************************************************************************/
/*                                                                              */
/*      Bubble creation methods                                                 */
/*                                                                              */
/********************************************************************************/

@Override public BudaBubble createBubble(BudaBubbleArea bba,Element xml)
{
   Element cnt = IvyXml.getChild(xml,"CONTENT");
   String typ = IvyXml.getAttrString(cnt,"TYPE");
   
   BudaBubble bb = null;
   if (typ.equals("CHAT")) {
      String name = IvyXml.getAttrString(cnt,"NAME");
      String cnts = IvyXml.getTextElement(cnt,"TEXT");
      String inp = IvyXml.getTextElement(cnt,"INPUT");
      bb = new BaitChatBubble(name,cnts,inp);  
    }

   return bb;
}


@Override public boolean matchBubble(BudaBubble bb,Element xml)
{
   Element cnt = IvyXml.getChild(xml,"CONTENT");
   String typ = IvyXml.getAttrString(cnt,"TYPE");
   if (typ.equals("CHAT") && bb instanceof BaitChatBubble) {
      BaitChatBubble nb = (BaitChatBubble) bb;
      String name = IvyXml.getAttrString(cnt,"NAME");
      String bname = nb.getChatName(); 
      if (name.equals(bname)) return true;
    }

   return false;
}

}       // end of class BaitConfigurator




/* end of BaitConfigurator.java */

