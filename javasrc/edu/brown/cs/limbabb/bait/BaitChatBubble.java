/********************************************************************************/
/*                                                                              */
/*              BaitChatBubble.java                                             */
/*                                                                              */
/*      Bubbles for chatting the LIMBA                                          */
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

import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.JEditorPane;
import javax.swing.JPopupMenu;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.w3c.dom.Element;

import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.bubbles.buda.BudaConstants;
import edu.brown.cs.bubbles.buda.BudaXmlWriter;
import edu.brown.cs.ivy.file.IvyFormat;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.limbabb.bait.BaitConstants.BaitInputListener;

class BaitChatBubble extends BudaBubble implements BaitConstants,
      BudaConstants.BudaBubbleOutputer, BaitInputListener
{
 

/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private BaitTerminalPanel terminal_panel;
private JEditorPane     input_area;
private JEditorPane     log_pane;
private boolean         use_context;
private String          history_id;  
private String          chat_name;

private static final long serialVersionUID = 1;
private static AtomicInteger history_counter = new AtomicInteger();
private static SimpleDateFormat file_dateformat = new SimpleDateFormat("yyMMddHHmmss");


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

BaitChatBubble()
{
   input_area = null;
   log_pane = null;
   use_context = true;
   history_id = "LIMBA_CHAT_" + history_counter.incrementAndGet();
   String rid = Integer.toString((int) (Math.random() * 10000));
   String fnm = "BirdChat_" + file_dateformat.format(new Date()) + "_" + rid + ".html";
   chat_name = fnm;
   
   terminal_panel = new BaitTerminalPanel();
   terminal_panel.addInputListener(this);
   
   setContentPane(terminal_panel.getComponent());
}


BaitChatBubble(String name,String cnts,String inp)
{
   this();
   
   if (name != null) {
      chat_name = name;
    }
   
   terminal_panel.initializeContents(cnts,inp); 
}


@Override protected void localDispose()
{
   // remove chat history
}


/********************************************************************************/
/*                                                                              */
/*      Configurator interface                                                  */
/*                                                                              */
/********************************************************************************/

@Override public String getConfigurator()                       { return "BAIT"; }

@Override public void outputXml(BudaXmlWriter xw) 
{
   xw.field("TYPE","CHAT");
   xw.field("NAME",chat_name);
   xw.cdataElement("TEXT",log_pane.getText());
   xw.cdataElement("INPUT",input_area.getText());
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
   
   menu.add(new UseContextAction());
   menu.add(new ClearContextAction());
   
   terminal_panel.addPopupButtons(evt,menu);
   
   // if inside Java Code, provide option to extract that code into a method somewhere
   
   menu.add(getFloatBubbleAction());
   
   menu.show(this,evt.getX(),evt.getY());
}


/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

void appendQuery(String text) 
{
   String text1 = IvyFormat.formatText(text);
   String disp = "<div align='right'><p style='text-indent: 50px;'><font color='blue'>" + 
         text1 + "</font></p></div>";
   appendOutput(disp);
}


void appendResponse(String text)
{
   String text1 = IvyFormat.formatText(text);
   
   String disp = "<div align='left'><p><font color='black'>" + text1 +
         "</font></p></div><hl>";
   appendOutput(disp);    
}


private void appendOutput(String s)
{
   try {
      HTMLEditorKit kit = (HTMLEditorKit) log_pane.getEditorKit();
      HTMLDocument doc = (HTMLDocument) log_pane.getDocument();
      kit.insertHTML(doc,doc.getLength(),s,
            0,0,null);
    }
   catch (Exception e) { 
      BoardLog.logE("BAIT","Problem appending output",e);
    }
}



/********************************************************************************/
/*                                                                              */
/*      Action handlers                                                         */
/*                                                                              */
/********************************************************************************/

private final class UseContextAction extends AbstractAction { 
   
   private static final long serialVersionUID = 1;
   
   UseContextAction() {
      super(use_context ? "Don't Use History" : "Use History");
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      use_context = !use_context;
    }
   
}       // end of inner class UseContextAction


private final class ClearContextAction extends AbstractAction { 
   
   private static final long serialVersionUID = 1;
   
   ClearContextAction() {
      super("Clear History");
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      BaitFactory bf = BaitFactory.getFactory();
      bf.sendLimbaMessage("CLEAR",new CommandArgs("ID",history_id),null);
    }
   
}       // end of inner class ClearContextAction


 
@Override public void handleInput(String text)
{
   if (text == null || text.isBlank()) return;
   
   String query = text;
   
   CommandArgs args = null;
   if (use_context) {
      args = new CommandArgs("ID",history_id);
    }
   
   BaitFactory.getFactory().issueCommand("QUERY",args,
         "CONTENTS",query,new Responder());  
}


private final class Responder implements ResponseHandler {
   
   @Override public void handleResponse(Element xml) { 
      Element rslt = xml;
      if (!IvyXml.isElement(xml,"RESULT")) {
         rslt = IvyXml.getChild(xml,"RESULT");
       }
      String text = IvyXml.getTextElement(rslt,"RESPONSE");
      if (text == null) {
         BoardLog.logE("BAIT","Problem with chat response result: " +
               IvyXml.convertXmlToString(xml));
         text = "???";
       }
      
      appendResponse(text);
    }
   
}       // end of inner class ResponseAction



}       // end of class BaitChatBubble




/* end of BaitChatBubble.java */

