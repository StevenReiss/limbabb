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

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.w3c.dom.Element;

import edu.brown.cs.bubbles.board.BoardImage;
import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.ivy.swing.SwingGridPanel;
import edu.brown.cs.ivy.xml.IvyXml;

class BaitChatBubble extends BudaBubble implements BaitConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private JButton         settings_button;
private JButton         submit_button;
private JEditorPane     input_area;
private JEditorPane     log_pane;

private static final long serialVersionUID = 1;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

BaitChatBubble()
{
   settings_button = null;
   submit_button = null;
   input_area = null;
   log_pane = null;
   
   JComponent pnl = getChatPanel();
   setContentPane(pnl);
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
   settings_button = new JButton(BoardImage.getIcon("settings"));
   settings_button.addActionListener(new SettingsAction());
   log_pane = new JEditorPane("text/html","");
   log_pane.setEditable(false);
   BoardLog.logD("BAIT","Chat panel log pane " + log_pane.getContentType() + " " +
         log_pane.getEditorKit() + " " +
         log_pane.getEditorKitForContentType("text/html"));
   
   JScrollPane outregion = new JScrollPane(log_pane,
         JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
         JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
   toppane.addGBComponent(toplabel,0,0,1,1,10,0);
   toppane.addGBComponent(settings_button,1,0,1,1,0,0);
   toppane.addGBComponent(outregion,0,1,0,1,10,10);
   toppane.setPreferredSize(new Dimension(400,300));
   
   SwingGridPanel botpane = new SwingGridPanel();
   JLabel botlabel = new JLabel("Enter Prompt");
   submit_button = new JButton("SUBMIT");
   submit_button.addActionListener(new SubmitAction());
   input_area = new JEditorPane("text/plain","");
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


private String formatText(String text)
{
   String ntext = text;
   ntext = ntext.replace("<","&lt;");
   ntext = ntext.replace(">","&gt;");
   
   for ( ; ; ) {
      int idx0 = ntext.indexOf("```");
      if (idx0 < 0) break;
      int idx1 = ntext.indexOf("\n",idx0);
      int idx2 = ntext.indexOf("```",idx1);
      int idx3 = ntext.length();
      if (idx2 < 0) {
         idx2 = ntext.length();
         idx3 = ntext.indexOf("\n",idx2);
       }
      
      String quote = ntext.substring(idx1,idx2);
      String pre = ntext.substring(0,idx0);
      String post = ntext.substring(idx3);
      ntext = pre + "<pre><code>\n" + quote + "\n" + post;
    }
   
   return ntext;
}

/********************************************************************************/
/*                                                                              */
/*      Action handlers                                                         */
/*                                                                              */
/********************************************************************************/

private final class SettingsAction implements ActionListener {
   
   @Override public void actionPerformed(ActionEvent evt) {
      
    }
   
}       // end of inner class SettingsAction


private final class SubmitAction implements ActionListener {
   
   @Override public void actionPerformed(ActionEvent evt) {
      String text = input_area.getText();
      if (text.isBlank()) return;
      BaitFactory.getFactory().issueCommand("QUERY",null,text,new Responder());  
      String disp = "<div align='right'><p style='text-indent: 50px;'><font color='blue'>" + text + 
            "</font></p></div>";
      appendOutput(disp);
    }
   
}       // end of inner class SubmitAction


private final class Responder implements ResponseHandler {
   
   @Override public void handleResponse(Element xml) { 
      Element rslt = IvyXml.getChild(xml,"RESULT");
      String text = IvyXml.getTextElement(rslt,"RESPONSE");
      text = formatText(text);
      String disp = "<div align='left'><p><font color='black'>" + text +
           "</font></p></div>";
      appendOutput(disp);    
    }
   
}       // end of inner class ResponseAction

}       // end of class BaitChatBubble




/* end of BaitChatBubble.java */

