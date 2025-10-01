/********************************************************************************/
/*                                                                              */
/*              BaitGenerateBubble.java                                         */
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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.BadLocationException;

import org.w3c.dom.Element;

import edu.brown.cs.bubbles.bale.BaleConstants;
import edu.brown.cs.bubbles.bale.BaleFactory;
import edu.brown.cs.bubbles.batt.BattFactory;
import edu.brown.cs.bubbles.batt.BattConstants;
import edu.brown.cs.bubbles.batt.BattConstants.BattNewTestPanel;
import edu.brown.cs.bubbles.batt.BattConstants.BattTest;
import edu.brown.cs.bubbles.board.BoardColors;
import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.board.BoardSetup;
import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.bubbles.buda.BudaBubbleArea;
import edu.brown.cs.bubbles.buda.BudaBubbleLink;
import edu.brown.cs.bubbles.buda.BudaConstants;
import edu.brown.cs.bubbles.buda.BudaDefaultPort;
import edu.brown.cs.bubbles.buda.BudaRoot;
import edu.brown.cs.bubbles.bump.BumpLocation;
import edu.brown.cs.bubbles.buss.BussConstants.BussEntry;
import edu.brown.cs.bubbles.buss.BussFactory;
import edu.brown.cs.ivy.swing.SwingGridPanel;
import edu.brown.cs.ivy.swing.SwingListPanel;
import edu.brown.cs.ivy.swing.SwingListSet;
import edu.brown.cs.ivy.swing.SwingTextArea;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class BaitGenerateBubble extends BudaBubble implements BaitConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private transient BumpLocation bump_location;
private BudaBubble source_bubble;
private SwingListSet<BaitUserFile> data_files;
private JTextArea prompt_field;
private JComboBox<TestChoice> type_field;
private TestChoice test_type;
private JButton generate_button;
private JCheckBox context_field;
private JButton data_button;
private transient TestCasePanel case_panel;
private JPanel test_panel;
private JLabel status_field;
private transient TestAction iotest_action;
private transient BattNewTestPanel iotest_panel;
private transient UserCodePanel user_panel;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

BaitGenerateBubble(BumpLocation loc,BudaBubble src,boolean usetests)
{
   bump_location = loc;
   source_bubble = src;
   test_type = null;
   
   iotest_action = new TestAction(loc);
   iotest_panel = BattFactory.getFactory().createNewTestPanel(iotest_action);
   String mthd = loc.getSymbolName();
   String prms = loc.getParameters();
   if (prms != null) mthd += prms;
   List<BattTest> tests = BattFactory.getFactory().getAllTestCases(mthd);
   case_panel = null;
   if (tests != null && tests.size() > 0) case_panel = new TestCasePanel(tests);
   user_panel = new UserCodePanel();
   
   JPanel pnl = setupPanel(usetests);
   setInteriorColor(BoardColors.getColor("Bait.TestCaseInterior"));
   setContentPane(pnl);
   
   loadDataFiles();
   scanForPrompt();
}



/********************************************************************************/
/*                                                                              */
/*      Panel setup                                                             */
/*                                                                              */
/********************************************************************************/

private JPanel setupPanel(boolean usetests)
{
   SwingGridPanel pnl = new GeneratePanel();
   pnl.setOpaque(false);
   pnl.beginLayout();
   String mthd = bump_location.getSymbolName();
   String ttl = "Generate Code for " + mthd;
   pnl.addBannerLabel(ttl);
   PromptCallback cb1 = new PromptCallback();
   prompt_field = pnl.addTextArea("Prompt",null,10,60,cb1);
   context_field = pnl.addBoolean("Use Context",false,null);
   if (usetests) {
      data_button = new JButton("Setup Context Data Files");
      DataCallback cb2 = new DataCallback();
      data_button.addActionListener(cb2);
      pnl.addRawComponent(null,data_button);
    }
   
   if (usetests) {
      TestTypeCallback cb3 = new TestTypeCallback();
      test_type = TestChoice.INPUT_OUTPUT;
      type_field = pnl.addChoice("Input Mode",test_type,cb3);
      if (case_panel == null) type_field.removeItem(TestChoice.TEST_CASES);
      test_panel = new JPanel(new BorderLayout());
      pnl.addSeparator();
      pnl.addRawComponent("TESTS",test_panel);
    }
   
   pnl.addSeparator();
   
   GenerateCallback cb4 = new GenerateCallback();
   generate_button = pnl.addBottomButton("GENERATE","GENERATE",cb4);
   generate_button.setEnabled(false);
   pnl.addBottomButtons();
   pnl.addSeparator();
   
   status_field = new JLabel();
   status_field.setOpaque(false);
   pnl.addLabellessRawComponent("STATUS",status_field);
   
   if (usetests) setupTestPanel(TestChoice.INPUT_OUTPUT);
   
   return pnl; 
}


private void setupTestPanel(TestChoice tc)
{
   if (test_panel == null) return;
   
   JComponent c = null;
   test_type = tc;
   switch (tc) {
      case INPUT_OUTPUT :
	 c = iotest_panel.getPanel();
	 break;
      case USER_TEST :
	 c = user_panel.getComponent();
	 break;
      case TEST_CASES :
	 c = case_panel.getComponent();
	 break;
    }
   
   if (c != null) {
      test_panel.removeAll();
      test_panel.add(c,BorderLayout.CENTER);
    }
   
   BudaBubble bb = BudaRoot.findBudaBubble(test_panel);
   if (bb != null) {
      Dimension sz = bb.getPreferredSize();
      System.err.println("PREF SIZE = " + sz + " " + bb.getSize());
      bb.setSize(sz);
    }
}


/********************************************************************************/
/*										*/
/*	Handle actions and updates						*/
/*										*/
/********************************************************************************/

private void checkStatus()
{
   boolean sts = true;
   if (prompt_field == null) sts = false;
   else if (prompt_field.getText().isBlank()) sts = false;
   
   if (type_field != null) {
      TestChoice tc = (TestChoice) type_field.getSelectedItem();
      switch (tc) {
	 case INPUT_OUTPUT :
	    sts &= iotest_panel.validate();
	    break;
	 case USER_TEST :
	    sts &= user_panel.validate();
	    break;
	 case TEST_CASES :
	    sts &= case_panel.validate();
	    break;
       }
    }
   
   if (generate_button != null) generate_button.setEnabled(sts);
}


private final class PromptCallback implements UndoableEditListener {
   
   @Override public void undoableEditHappened(UndoableEditEvent evt) {
      checkStatus();
    }
   
}       // end of inner class PromptCallback


private final class DataCallback implements ActionListener {
   
   @Override public void actionPerformed(ActionEvent evt) {
      DataFilePanel dfp = new DataFilePanel();
      JOptionPane.showInputDialog(BaitGenerateBubble.this,dfp);
    }
   
}       // end of inner class DataCallback

private final class TestTypeCallback implements ActionListener {
   
   @Override public void actionPerformed(ActionEvent evt) {
      TestChoice tc = (TestChoice) type_field.getSelectedItem();
      if (tc == test_type) return;
      setupTestPanel(tc);
    }
   
}       // end of inner class TestTypeCallback



private final class GenerateCallback implements ActionListener {
   
   @Override public void actionPerformed(ActionEvent evt) {
      doGenerate();
    }
   
}       // end of inner class GenerateCallback



private final class GeneratePanel extends SwingGridPanel {

   private static final long serialVersionUID = 1;
   
   @Override public void paintComponent(Graphics g) {
      g.setColor(getInteriorColor());
      Dimension d = getSize();
      g.fillRect(0, 0, d.width, d.height);
    }
   
}	// end of inner class TestPanel



/********************************************************************************/
/*                                                                              */
/*      Generate action                                                         */
/*                                                                              */
/********************************************************************************/

private void doGenerate()
{
   BoardLog.logD("BAIT","GENERATE CODE USING LIMBA");
   
   BaitGenerateEngine eng = new BaitGenerateEngine(bump_location);
   boolean needctx = false;
   if (test_type == TestChoice.TEST_CASES) needctx = true;
   if (test_type != null && context_field.isSelected()) needctx = true;
   if (data_files.getSize() > 0) needctx = true;
   if (needctx) {
      eng.setDataFiles(data_files.getElements());
    }
   eng.setContextFlag(context_field.isSelected());
   
   eng.setDescription(prompt_field.getText()); 
   if (test_type != null) {
      switch (test_type) {
         case INPUT_OUTPUT :
            eng.setTestCases(iotest_panel.getActiveTests());
            break;
         case USER_TEST :
            eng.setTestCode(user_panel.getTestCode());
            break;
         case TEST_CASES :
            // eng.setTestCode(case_panel.getTestCode());
            eng.setUserTest(case_panel.getUserTests());
            break;
       }
    }
   
   GenerateRequest rq = new GenerateRequest();
   
   eng.startSearch(rq);
   
   status_field.setText("Generating Code ...");
   BoardColors.setColors(status_field,"Bait.TestCaseStatus");
   status_field.setOpaque(true);
   Dimension d = getPreferredSize();
   setSize(d);
}



private class GenerateRequest implements BaitGenerateRequest {
   
   GenerateRequest() { }
   
   @Override public void handleGenerateFailed() {
      status_field.setText("Nothing found from code search");
      BoardColors.setColors(status_field,"Bait.TestCaseFailed");
    }
   
   @Override public void handleGenerateSucceeded(List<BaitGenerateResult> result) { 
      status_field.setText("Search completed");
      BoardColors.setColors(status_field,"Bait.TestCaseSuccess");
      List<BussEntry> sols = new ArrayList<>();
      for (BaitGenerateResult bsr : result) {
         sols.add(new BaitGenerateSolution(bsr,bump_location,source_bubble));    
       }
      BudaBubble bb = BussFactory.getFactory().createBubbleStack(sols,450);
      BudaBubbleArea bba = BudaRoot.findBudaBubbleArea(BaitGenerateBubble.this);
      bba.addBubble(bb,BaitGenerateBubble.this,null,
            BudaConstants.PLACEMENT_LOGICAL|
            BudaConstants.PLACEMENT_GROUPED|BudaConstants.PLACEMENT_NEW);
      BudaBubbleLink bbl = new BudaBubbleLink(BaitGenerateBubble.this,
            new BudaDefaultPort(),bb,new BudaDefaultPort());
      bba.addLink(bbl);
    }
   
   @Override public void handleGenerateInputs(List<BaitGenerateInput> result) { }
   
}	// end of inner class SearchRequest


/********************************************************************************/
/*                                                                              */
/*      Automatically scan for prompt                                           */
/*                                                                              */
/********************************************************************************/

private void scanForPrompt()
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
      return;
    }
   
   boolean inlinecmmt = false;
   boolean inareacmmt = false;
   char lastchar = 0;
   boolean startline = false;
   StringBuffer text = new StringBuffer();
   for (int i = 0; i < code.length(); ++i) {
      char ch = code.charAt(i);
      if (inlinecmmt) { 			// check for end of comment
	 if (ch == '\n') {
	    inlinecmmt = false;
            if (!text.isEmpty()) text.append("\n");
          }
       }
      else if (inareacmmt) {
	 if (lastchar == '*' && ch == '/') {
            int ln = text.length();
            for (int j = ln-1; j >= 0; --j) {
               Character c1 = text.charAt(j);
               if (Character.isWhitespace(c1) || c1 == '*' || c1 == '/') continue;
               text = text.delete(j,ln);
               break;
             }
	    inareacmmt = false;
            if (!text.isEmpty()) text.append("\n");
	  }
       }
      
      if (inareacmmt || inlinecmmt) {
         if (!startline || (ch != '/' && ch != '*' && !Character.isWhitespace(ch))) {
            text.append(ch);
            startline = false;
          }
         if (ch == '\n') startline = true;
       }
      else if (Character.isLetterOrDigit(ch)) {
	 break;
       }
      else if (lastchar == '/') {
	 if (ch == '/') {
	    inlinecmmt = true;
            startline = true;
	  }
	 else if (ch == '*') {
	    inareacmmt = true;
            startline = true;
	  }
       }
      else if (ch == '\n') {
         startline = true;
       }
      lastchar = ch;
    }
   
   String txt = text.toString().trim();
   prompt_field.setText(txt);
}


/********************************************************************************/
/*										*/
/*	Test case panel 							*/
/*										*/
/********************************************************************************/

private class TestCasePanel implements ListSelectionListener {
   
   private JList<BattTest> list_component;
   private JScrollPane scroll_pane;
   
   TestCasePanel(Collection<BattTest> tests) {
      Vector<BattTest> vd = new Vector<BattTest>(tests);
      list_component = new JList<BattTest>(vd);
      list_component.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      list_component.setVisibleRowCount(10);
      list_component.addListSelectionListener(this);
      scroll_pane = new JScrollPane(list_component);
      
      for (int i = 0; i < vd.size(); ++i) {
         BattTest bt = vd.get(i);
         switch (bt.usesMethod(bump_location.getSymbolName())) {
            case DIRECT :
               list_component.addSelectionInterval(i,i);
               break;
            default :
               break;
          }
       }
    }
   
   JComponent getComponent()		{ return scroll_pane; }
   
   List<BattTest> getUserTests() {
      List<BattTest> rslt = new ArrayList<BattTest>();
      for (BattTest bt : list_component.getSelectedValuesList()) {
         rslt.add(bt);
       }
      return rslt;
    }
   
   boolean validate() {
      List<BattTest> v = list_component.getSelectedValuesList();
      if (v == null || v.size() == 0) return false;
      
      return true;
    }
   
   @Override public void valueChanged(ListSelectionEvent e) {
      checkStatus();
    }
   
}	// end of inner class TestCasePanel



/********************************************************************************/
/*										*/
/*	User code panel 							*/
/*										*/
/********************************************************************************/

private class UserCodePanel implements CaretListener {
   
   private JTextArea	code_area;
   private JScrollPane	scroll_pane;
   
   UserCodePanel() {
      code_area = new SwingTextArea(5,40);
      code_area.setWrapStyleWord(true);
      code_area.addCaretListener(this);
      scroll_pane = new JScrollPane(code_area);
    }
   
   JComponent getComponent()		{ return scroll_pane; }
   
   String getTestCode() {
      return code_area.getText().trim();
    }
   
   boolean validate() {
      String txt = code_area.getText();
      if (txt.length() < 4) return false;
      return true;
    }
   
   @Override public void caretUpdate(CaretEvent e) {
      checkStatus();
    }
   
}	// end of inner class UserCodePanel


/********************************************************************************/
/*										*/
/*	Data and callback handler for iotest panel				*/
/*										*/
/********************************************************************************/

private class TestAction implements BattConstants.BattTestBubbleCallback {

   private BumpLocation for_location;
   private BattConstants.NewTestMode test_mode;
   
   TestAction(BumpLocation loc) {
      for_location = loc;
      test_mode = BattConstants.NewTestMode.INPUT_OUTPUT;
    }
   
   @Override public String getButtonName()			{ return "Start Code Search"; }
   @Override public BumpLocation getLocation()			{ return for_location; }
   @Override public BattConstants.NewTestMode getTestMode()	{ return test_mode; }
   @Override public String getClassName()			{ return null; }
   @Override public boolean getCreateClass()			{ return false; }
   
   @Override public void itemUpdated() {
      checkStatus();
    }
   
   @Override public boolean handleTestCases(List<BattConstants.BattCallTest> cts) {
      BoardLog.logD("BAIT","START SEARCH WITH " + cts);
      return true;
    }
   
   @Override public void handleTestCases(String code) {
      BoardLog.logD("BAIT","START SEARCH WITH " + code);
    }
   
}	// end of inner class TestAction


/********************************************************************************/
/*										*/
/*	Panel for handling data files						*/
/*										*/
/********************************************************************************/

private void loadDataFiles()
{
   data_files = new SwingListSet<BaitUserFile>();
   
   File f = BoardSetup.getBubblesWorkingDirectory();
   File f1 = new File(f,"bait.datafiles");
   if (!f1.exists() || f1.length() == 0) return;
   
   Element xml = IvyXml.loadXmlFromFile(f1);
   for (Element uf : IvyXml.children(xml,"USERFILE")) {
      String nm = IvyXml.getTextElement(uf,"NAME");
      String anm = IvyXml.getTextElement(uf,"JARNAME");
      UserFileType md = IvyXml.getAttrEnum(uf,"ACCESS",UserFileType.READ);
      BaitUserFile buf = new BaitUserFile(new File(nm),anm,md);
      data_files.addElement(buf);
    }
}



private void saveDataFiles()
{
   File f = BoardSetup.getBubblesWorkingDirectory();
   File f1 = new File(f,"bait.datafiles");
   try {
      IvyXmlWriter xw = new IvyXmlWriter(f1);
      xw.begin("DATAFILES");
      for (BaitUserFile df : data_files) {
	 df.addEntry(xw);
       }
      xw.end("DATAFILES");
      xw.close();
    }
   catch (IOException e) { }
}



private boolean editUserFile(BaitUserFile uf)
{
   SwingGridPanel pnl = new SwingGridPanel();
   
   pnl.beginLayout();
   pnl.addBannerLabel("Edit User Data File");
   JTextField lnm = pnl.addFileField("Local File",uf.getFileName(),JFileChooser.FILES_ONLY,null,null);
   JTextField rnm = pnl.addTextField("Remove File (/s6/ or s:)",uf.getAccessName(),null,null);
   JComboBox<UserFileType> typ = pnl.addChoice("Access",UserFileType.READ,null);
   
   int fg = JOptionPane.showOptionDialog(this,pnl,"Edit User Data File",
	 JOptionPane.OK_CANCEL_OPTION,
	 JOptionPane.PLAIN_MESSAGE,
	 null,null,null);
   if (fg != 0) return false;
   
   String l = lnm.getText();
   String r = rnm.getText();
   UserFileType ft = (UserFileType) typ.getSelectedItem();
   uf.set(new File(l),r,ft);
   
   return true;
}




private class DataFilePanel extends SwingListPanel<BaitUserFile> {
   
   private static final long serialVersionUID = 1;
   
   DataFilePanel() {
      super(data_files);
    }
   
   @Override protected BaitUserFile createNewItem() {
      BaitUserFile uf = new BaitUserFile();
      if (editUserFile(uf)) {
         data_files.addElement(uf);
         saveDataFiles();
       }
      return null;
    }
   
   @Override protected BaitUserFile editItem(Object itm) {
      BaitUserFile uf = (BaitUserFile) itm;
      if (editUserFile(uf)) saveDataFiles();
      return uf;
    }
   
   @Override protected BaitUserFile deleteItem(Object itm) {
      BaitUserFile uf = (BaitUserFile) itm;
      data_files.removeElement(uf);
      saveDataFiles();
      return null;
    }
   
}	// end of inner class DataFilePanel





}       // end of class BaitGenerateBubble




/* end of BaitGenerateBubble.java */

