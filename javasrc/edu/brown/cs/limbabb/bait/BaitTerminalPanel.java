/********************************************************************************/
/*                                                                              */
/*              BaitTerminalPanel.java                                          */
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

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.file.IvyFormat;
import edu.brown.cs.ivy.swing.SwingEventListenerList;
import edu.brown.cs.ivy.swing.SwingGridPanel;
import edu.brown.cs.ivy.swing.SwingText;
import edu.brown.cs.ivy.swing.SwingWrappingEditorPane;

class BaitTerminalPanel implements BaitConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private JComponent      terminal_window;
private JButton         submit_button;
private JEditorPane     input_area;
private JEditorPane     log_pane;
private String          last_file;
private String          query_color;
private String          response_color;
private boolean         auto_scroll;
private SwingEventListenerList<BaitInputListener> input_listeners; 



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

BaitTerminalPanel()
{
   input_listeners = new SwingEventListenerList<>(BaitInputListener.class);
   last_file = null;
   terminal_window = null;
   submit_button = null;
   input_area = null;
   log_pane = null;
   query_color = "blue";
   response_color = "black";
   auto_scroll = true;
   
   setupPanel();
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

JComponent getComponent()
{
   return terminal_window;
}


String getText()
{
   return log_pane.getText();
}


String getInputText()
{
   return input_area.getText();
}


void setQueryColor(String c) 
{
   query_color = c;
}


void setResponseColor(String c)
{
   response_color = c;
}

void addInputListener(BaitInputListener il)
{
   input_listeners.add(il);
}


void removeInputListener(BaitInputListener il)
{
   input_listeners.remove(il);
}

void enableInput(boolean fg)
{
   submit_button.setEnabled(fg);
}


/********************************************************************************/
/*                                                                              */
/*      Popup Menu Methods                                                      */
/*                                                                              */
/********************************************************************************/

void addPopupButtons(MouseEvent evt,JPopupMenu menu)
{
   menu.add(new ImportAction());
   menu.add(new SaveAction());
   menu.add(new PrintAction());
   // auto-scroll button
}



/********************************************************************************/
/*                                                                              */
/*      Setup methods                                                           */
/*                                                                              */
/********************************************************************************/

void setupPanel()
{
   SwingGridPanel toppane = new SwingGridPanel();
   JLabel toplabel = new JLabel("Limba CHAT");
   log_pane = new SwingWrappingEditorPane("text/html","");
   log_pane.setEditable(false);
   BoardLog.logD("BAIT","Chat panel log pane " + log_pane.getContentType() + " " +
         log_pane.getEditorKit() + " " +
         log_pane.getEditorKitForContentType("text/html"));
   
   JScrollPane outregion = new JScrollPane(toppane,
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
   
   terminal_window = split;
}



/********************************************************************************/
/*                                                                              */
/*      Teminal update methods                                                  */
/*                                                                              */
/********************************************************************************/

void appendQuery(String text) 
{
   String text1 = IvyFormat.formatText(text);
   String disp = "<div align='right'><p style='text-indent: 50px;'><font color='" +
         query_color + "'>" + text1 + "</font></p></div>";
   appendOutput(disp);
}


void appendResponse(String text)
{
   String text1 = IvyFormat.formatText(text);
   
   String disp = "<div align='left'><p><font color='" + response_color +
         "'>" + text1 + "</font></p></div><hl>";
   appendOutput(disp);    
}


void initializeContents(String cnts,String inp)
{
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

private final class SubmitAction implements ActionListener {
   
   @Override public void actionPerformed(ActionEvent evt) {
      String text = input_area.getText();
      if (text.isBlank()) return;
      appendQuery(text);
      
      for (BaitInputListener al : input_listeners) {
         al.handleInput(text);
       }
      
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
      int retval = chooser.showSaveDialog(terminal_window);
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


private final class ImportAction extends AbstractAction {
   
   private static final long serialVersionUID = 1;
   
   ImportAction() {
      super("Import File");
    }
   
   @Override public void actionPerformed(ActionEvent evt) {
      SwingGridPanel pnl = new SwingGridPanel();
      pnl.addBannerLabel("Import from File");
      JTextField lnm = pnl.addFileField("File",last_file,
            JFileChooser.FILES_ONLY,null,null);
      int fg = JOptionPane.showOptionDialog(terminal_window,pnl,
            "Import From File",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,null,null);
      if (fg != JOptionPane.OK_OPTION) return;
      String fnm = lnm.getText();
      last_file = fnm;
      try {
         String txt = IvyFile.loadFile(new File(fnm));
         Document doc = input_area.getDocument();
         String dtxt = doc.getText(0,doc.getLength());
         if (!dtxt.endsWith("\n")) txt = "\n" + txt;
         doc.insertString(doc.getLength(),txt,null);
       }
      catch (BadLocationException e) { }
      catch (IOException e) { }
    }
   
}       // end of inner class ImportAction




}       // end of class BaitTerminalPanel




/* end of BaitTerminalPanel.java */

