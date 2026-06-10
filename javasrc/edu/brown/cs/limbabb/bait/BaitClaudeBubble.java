/********************************************************************************/
/*                                                                              */
/*              BaitClaudeBubble.java                                           */
/*                                                                              */
/*      Terminal to talk to Claude Code instance                                */
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

import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JPopupMenu;

import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.bubbles.buda.BudaConstants;
import edu.brown.cs.bubbles.buda.BudaXmlWriter;
import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.limbabb.bait.BaitConstants.BaitInputListener;

class BaitClaudeBubble extends BudaBubble implements BaitConstants, 
      BudaConstants.BudaBubbleOutputer, BaitInputListener
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private BaitTerminalPanel terminal_panel;
private String chat_name;
private IvyExec claude_process;

private static final long serialVersionUID = 1;
private static SimpleDateFormat file_dateformat = new SimpleDateFormat("yyMMddHHmmss");



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

BaitClaudeBubble()
{
   String fnm = "BirdClaude_" + file_dateformat.format(new Date()) + ".html";
   chat_name = fnm;
   claude_process = null;
   
   
   // should create a grid panel with buttons for common operations
   terminal_panel = new BaitTerminalPanel();
   terminal_panel.addInputListener(this);
   terminal_panel.enableInput(false);
   
   // start up claude if we have a model -- enable input if successful
}


BaitClaudeBubble(String name,String cnts,String inp)
{
   this();
   
   if (name != null) {
      chat_name = name;
    }
   
   terminal_panel.initializeContents(cnts,inp); 
}




/********************************************************************************/
/*                                                                              */
/*      Configurator interface                                                  */
/*                                                                              */
/********************************************************************************/

@Override public String getConfigurator()                       { return "BAIT"; }

@Override public void outputXml(BudaXmlWriter xw) 
{
   xw.field("TYPE","CLAUDE");
   xw.field("NAME",chat_name);
   xw.cdataElement("TEXT",terminal_panel.getText());
   xw.cdataElement("INPUT",terminal_panel.getInputText());
}

String getChatName()                                            { return chat_name; }



/********************************************************************************/
/*                                                                              */
/*      Menu methods                                                            */
/*                                                                              */
/********************************************************************************/

@Override public void handlePopupMenu(MouseEvent evt)
{
   JPopupMenu menu = new JPopupMenu();
   
   terminal_panel.addPopupButtons(evt,menu);
   
   menu.add(getFloatBubbleAction());
   
   menu.show(this,evt.getX(),evt.getY());
}



/********************************************************************************/
/*                                                                              */
/*      Abstract Method Implementations                                         */
/*                                                                              */
/********************************************************************************/

@Override public void handleInput(String text)
{
   if (claude_process == null) return;
   // send text to CLAUDE CODEfg);
   
   // method body goes here
}



}       // end of class BaitClaudeBubble




/* end of BaitClaudeBubble.java */

