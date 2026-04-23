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
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.w3c.dom.Element;

import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.bubbles.buda.BudaConstants;
import edu.brown.cs.bubbles.buda.BudaXmlWriter;
import edu.brown.cs.ivy.file.IvyFormat;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.swing.SwingGridPanel;
import edu.brown.cs.ivy.swing.SwingText;
import edu.brown.cs.ivy.swing.SwingWrappingEditorPane;
import edu.brown.cs.ivy.xml.IvyXml;

class BaitChatBubble extends BudaBubble implements BaitConstants,
      BudaConstants.BudaBubbleOutputer
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
private String          chat_name;
private boolean         auto_scroll;

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
   submit_button = null;
   input_area = null;
   log_pane = null;
   use_context = true;
   history_id = "LIMBA_CHAT_" + history_counter.incrementAndGet();
   String rid = Integer.toString((int) (Math.random() * 10000));
   String fnm = "BirdChat_" + file_dateformat.format(new Date()) + "_" + rid + ".html";
   chat_name = fnm;
   auto_scroll = true;
   
   JComponent pnl = getChatPanel();
   setContentPane(pnl);
}


BaitChatBubble(String name,String cnts,String inp)
{
   this();
   
   if (name != null) {
      chat_name = name;
    }
   if (cnts != null) {
      try {
         HTMLEditorKit kit = (HTMLEditorKit) log_pane.getEditorKit();
         HTMLDocument doc = (HTMLDocument) log_pane.getDocument();
         kit.insertHTML(doc,doc.getLength(),cnts,
               0,0,null);
         if (auto_scroll) {
            Rectangle r = SwingText.modelToView2D(log_pane,doc.getLength()-1);
            if (r != null) {
               Dimension sz = log_pane.getSize();
               r.x = 0;
               r.y += 20;
               if (r.y + r.height > sz.height) r.y = sz.height;
               log_pane.scrollRectToVisible(r);
             }
          }
       }
      catch (Exception e) {
         BoardLog.logE("BAIT","Problem reloading chat",e);
       }
    }
   if (inp != null) {
      input_area.setText(inp);
    }
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
   menu.add(new SaveAction());
   menu.add(new PrintAction());
   
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



private final class SaveAction extends AbstractAction {

   SaveAction() {
      super("Save Transcript");
    }
   
   @Override public void actionPerformed(ActionEvent evt) {
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Choose html file to save transcript in");
      chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      chooser.setFileFilter(new FileNameExtensionFilter("HTML File",
            "html","htm"));
      int retval = chooser.showSaveDialog(BaitChatBubble.this);
      if (retval == JFileChooser.APPROVE_OPTION){
         File f = chooser.getSelectedFile();
         String cnts = log_pane.getText();
         // might need to prepend <html> and append \n
         try (FileWriter fw = new FileWriter(f)) {
            fw.write(cnts);
          }
         catch (IOException e) {
            BoardLog.logE("BAIT","Save to file " + f + " failed",e);
          }
       }
    }
   
}       // end of inner class SaveAction


private final class PrintAction extends AbstractAction {
   
   private static final long serialVersionUID = 1L;
   
   PrintAction() {
      super("Print Transcript");
    }
   
   @Override public void actionPerformed(ActionEvent evt) {
      try {
         log_pane.print();
       }
      catch (java.awt.print.PrinterException e) {
         BoardLog.logE("BAIT","Problem printing transcript",e);
       }
    }
   
}       // end of inner class PrintAction



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
      
      text = IvyFormat.formatText(text);
   
      String disp = "<div align='left'><p><font color='black'>" + text +
           "</font></p></div><hl>";
      appendOutput(disp);    
    }
   
}       // end of inner class ResponseAction



}       // end of class BaitChatBubble




/* end of BaitChatBubble.java */

