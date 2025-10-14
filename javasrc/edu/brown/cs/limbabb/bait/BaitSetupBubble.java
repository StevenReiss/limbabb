/********************************************************************************/
/*                                                                              */
/*              BaitSetupBubble.java                                            */
/*                                                                              */
/*      Let the user set properties for using LIMBA                             */
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
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.w3c.dom.Element;

import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.board.BoardProperties;
import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.swing.SwingGridPanel;
import edu.brown.cs.ivy.xml.IvyXml;

class BaitSetupBubble extends BudaBubble implements BaitConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private JComboBox<String>       model_chooser;
private JTextArea               style_area;
private JTextArea               context_area;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

BaitSetupBubble()
{
   model_chooser = null;
   style_area = null;
   context_area = null;
   
   JComponent pnl = getSetupPanel();
   setContentPane(pnl);
}



/********************************************************************************/
/*                                                                              */
/*      Setup the panel                                                         */
/*                                                                              */
/********************************************************************************/

JComponent getSetupPanel()
{
   BaitFactory bf = BaitFactory.getFactory();
   Element mdls = bf.sendLimbaMessage("LIST",null,null);
   if (!IvyXml.isElement(mdls,"RESULT")) {
      mdls = IvyXml.getChild(mdls,"RESULT");
    }
   Set<String> mdlist = new TreeSet<>();
   String dflt = null;
   for (Element mdl : IvyXml.children(mdls,"MODEL")) {
      String mnm = IvyXml.getText(mdl);
      if (mnm != null) {
         mnm = mnm.trim();
         if (mnm.contains("embed")) continue;
         if (dflt != null) dflt = mnm;
         mdlist.add(mnm);
       }
    }
   
   BoardProperties bp = BoardProperties.getProperties("Bait");
   
   SwingGridPanel pnl = new SwingGridPanel();
   pnl.beginLayout();
   pnl.addBannerLabel("LIMBA (AI) Setup Panel");
   pnl.addSeparator();
   
   String usrmdl = bp.getProperty("Bait.ollama.model");
   if (usrmdl != null && !mdlist.contains(usrmdl)) {
      usrmdl = null;
    }
   if (usrmdl == null) usrmdl = dflt;
   
   if (mdlist.size() > 1) {
      model_chooser = pnl.addChoice("Model",mdlist,usrmdl,null);
    }
   else if (usrmdl != null) {
      String [] choice = new String[] { usrmdl };
      model_chooser = new JComboBox<>(choice);
    }
   pnl.addSeparator();
   style_area = pnl.addTextArea("Code Style",bp.getProperty("Bait.ollama.style"),null);
   pnl.addFileField("",(File) null,JFileChooser.FILES_ONLY,
         new StyleFileAction(),null);
   pnl.addSeparator();
   pnl.addTextArea("Code Context",bp.getProperty("Bait.ollama.context"),null);
   pnl.addFileField("",(File) null,JFileChooser.FILES_ONLY,
         new ContextFileAction(),null);
   pnl.addSeparator(); 
   
   pnl.addBottomButton("CANCEL","CANCEL",new CancelHandler());
   pnl.addBottomButton("SAVE","SAVE",new SaveHandler());
   pnl.addBottomButton("UPDATE","UPDATE",new UpdateHandler());
   pnl.addBottomButtons();
   return pnl;
}



/********************************************************************************/
/*                                                                              */
/*      Listeners                                                               */
/*                                                                              */
/********************************************************************************/
   
private final class StyleFileAction implements ActionListener {
   
   @Override public void actionPerformed(ActionEvent evt) {
      JTextField tf = (JTextField) evt.getSource();
      String fnm = tf.getText();
      try {
         String cnts = IvyFile.loadFile(new File(fnm));
         String pvs = style_area.getText();
         if (pvs == null || pvs.isEmpty()) pvs = "";
         else if (!pvs.endsWith("\n")) pvs = pvs + "\n";
         style_area.setText(pvs + cnts);
       }
      catch (IOException e) { }
    }
   
}       // end of inner class StlyeFioleAction


private final class ContextFileAction implements ActionListener {
   
   @Override public void actionPerformed(ActionEvent evt) {
      JTextField tf = (JTextField) evt.getSource();
      String fnm = tf.getText();
      try {
         String cnts = IvyFile.loadFile(new File(fnm));
         String pvs = context_area.getText();
         if (pvs == null || pvs.isEmpty()) pvs = "";
         else if (!pvs.endsWith("\n")) pvs = pvs + "\n";
         context_area.setText(pvs + cnts);
       }
      catch (IOException e) { }
    }

}       // end of inner class StlyeFioleAction


private final class CancelHandler implements ActionListener {
   
   @Override public void actionPerformed(ActionEvent evt) {
      BaitSetupBubble.this.setVisible(false);
    }

}       // end of inner class CancelHandler


private final class SaveHandler implements ActionListener {
   
   @Override public void actionPerformed(ActionEvent evt) {
      BoardProperties bp = BoardProperties.getProperties("Bait");
      if (model_chooser != null) {
         String mdl = model_chooser.getItemAt(model_chooser.getSelectedIndex());
         bp.setProperty("Bait.ollama.model",mdl);
       }
      String sty = style_area.getText();
      bp.setProperty("Bait.ollama.style",sty);
      String ctx = context_area.getText();
      bp.setProperty("Bait.ollama.context",ctx);
      try {
         bp.save();
       }
      catch (IOException e) { 
         BoardLog.logE("BAIT","Problem saving properties",e);
       }
    }

}       // end of inner class CancelHandler




private final class UpdateHandler implements ActionListener {

   @Override public void actionPerformed(ActionEvent evt) {
      BaitFactory bf = BaitFactory.getFactory();
      if (model_chooser != null) {
         String mdl = model_chooser.getItemAt(model_chooser.getSelectedIndex());
         bf.sendLimbaMessage("SETMODEL",null,mdl);
       }
      String sty = style_area.getText();
      bf.sendLimbaMessage("STYLE",null,sty);
      String ctx = context_area.getText();
      bf.sendLimbaMessage("CONTEXT",null,ctx);
      
      BaitSetupBubble.this.setVisible(false);
    }

}       // end of inner class CancelHandler



}       // end of class BaitSetupBubble




/* end of BaitSetupBubble.java */

