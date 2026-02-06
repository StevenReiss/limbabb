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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.w3c.dom.Element;

import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.ivy.file.IvyFormat;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.swing.SwingGridPanel;
import edu.brown.cs.ivy.swing.SwingWrappingEditorPane;
import edu.brown.cs.ivy.xml.IvyXml;

class BaitChatBubble extends BudaBubble implements BaitConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private JButton         submit_button;
private JEditorPane     input_area;
private JEditorPane     log_pane;
private boolean         use_context;
private String          history_id;

private static final long serialVersionUID = 1;
private static AtomicInteger history_counter = new AtomicInteger();


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

BaitChatBubble()
{
   submit_button = null;
   input_area = null;
   log_pane = null;
   use_context = true;
   history_id = "LIMBA_CHAT_" + history_counter.incrementAndGet();
   
   JComponent pnl = getChatPanel();
   setContentPane(pnl);
}


@Override protected void localDispose()
{
   // remove chat history
}



/********************************************************************************/
/*                                                                              */
/*      Setup the internals                                                     */
/*                                                                              */
/********************************************************************************/

JComponent getChatPanel()
{
   SwingGridPanel toppane = new SwingGridPanel();
   JLabel toplabel = new JLabel("Limba CHAT");
   log_pane = new SwingWrappingEditorPane("text/html","");
   log_pane.setEditable(false);
   BoardLog.logD("BAIT","Chat panel log pane " + log_pane.getContentType() + " " +
         log_pane.getEditorKit() + " " +
         log_pane.getEditorKitForContentType("text/html"));
   
   JScrollPane outregion = new JScrollPane(log_pane,
         JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
         JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
   toppane.addGBComponent(toplabel,0,0,1,1,10,0);
   toppane.addGBComponent(outregion,0,1,0,1,10,10);
   toppane.setPreferredSize(new Dimension(400,300));
   
   SwingGridPanel botpane = new SwingGridPanel();
   JLabel botlabel = new JLabel("Enter Prompt");
   submit_button = new JButton("SUBMIT");
   submit_button.addActionListener(new SubmitAction());
   input_area = new SwingWrappingEditorPane("text/plain","");
   input_area.setEditable(true);
   JScrollPane inregion = new JScrollPane(input_area,
         JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
         JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
   botpane.addGBComponent(botlabel,0,0,1,1,10,0);
   botpane.addGBComponent(submit_button,1,0,1,1,0,0);
   botpane.addGBComponent(inregion,0,1,0,1,10,10);
   botpane.setPreferredSize(new Dimension(400,100));
   
   JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
         true,toppane,botpane);
   
   return split;
}



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
   
   // if inside Java Code, provide option to extract that code into a method somewhere
   
   menu.add(getFloatBubbleAction());
   
   menu.show(this,evt.getX(),evt.getY());
}


/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

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



private final class SubmitAction implements ActionListener {
   
   @Override public void actionPerformed(ActionEvent evt) {
      String text = input_area.getText();
      if (text.isBlank()) return;
      String query = text;
      CommandArgs args = null;
      if (use_context) {
         args = new CommandArgs("ID",history_id);
       }
      BaitFactory.getFactory().issueCommand("QUERY",args,
            "CONTENTS",query,new Responder());  
      String text1 = IvyFormat.formatText(text);
      String disp = "<div align='right'><p style='text-indent: 50px;'><font color='blue'>" + text1 + 
            "</font></p></div>";
      appendOutput(disp);
      
      input_area.setText("");
    }
   
}       // end of inner class SubmitAction


private final class Responder implements ResponseHandler {
   
   @Override public void handleResponse(Element xml) { 
      Element rslt = xml;
      if (!IvyXml.isElement(xml,"RESULT")) {
         rslt = IvyXml.getChild(xml,"RESULT");
       }
      String text = IvyXml.getTextElement(rslt,"RESPONSE");
      if (text == null) {
         BoardLog.logE("BAIT","Problem with response result: " +
               IvyXml.convertXmlToString(rslt));
         text = "???";
       }
      
      text = IvyFormat.formatText(text);
   
      String disp = "<div align='left'><p><font color='black'>" + text +
           "</font></p></div>";
      appendOutput(disp);    
    }
   
}       // end of inner class ResponseAction



}       // end of class BaitChatBubble




/* end of BaitChatBubble.java */

