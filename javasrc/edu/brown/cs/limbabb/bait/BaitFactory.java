/********************************************************************************/
/*										*/
/*		BaitFactory.java					       */
/*										*/
/*	Factory for setting up and interface semantic analysis with bubbles	*/
/*										*/
/********************************************************************************/
/*	Copyright 2011 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 * This program and the accompanying materials are made available under the	 *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at								 *
 *	http://www.eclipse.org/legal/epl-v10.html				 *
 *										 *
 ********************************************************************************/


package edu.brown.cs.limbabb.bait;

import edu.brown.cs.bubbles.bale.BaleConstants;
import edu.brown.cs.bubbles.bale.BaleFactory;
import edu.brown.cs.bubbles.bale.BaleConstants.BaleContextConfig;
import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.board.BoardPluginManager;
import edu.brown.cs.bubbles.board.BoardProperties;
import edu.brown.cs.bubbles.board.BoardSetup;
import edu.brown.cs.bubbles.board.BoardConstants.BoardPluginFilter;
import edu.brown.cs.bubbles.board.BoardConstants.RunMode;
import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.bubbles.buda.BudaBubbleArea;
import edu.brown.cs.bubbles.buda.BudaConstants;
import edu.brown.cs.bubbles.buda.BudaRoot;
import edu.brown.cs.bubbles.buda.BudaConstants.BudaBubblePosition;
import edu.brown.cs.bubbles.bump.BumpClient;
import edu.brown.cs.bubbles.bump.BumpLocation;
import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.ivy.exec.IvyExecQuery;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintArguments;
import edu.brown.cs.ivy.mint.MintConstants;
import edu.brown.cs.ivy.mint.MintControl;
import edu.brown.cs.ivy.mint.MintDefaultReply;
import edu.brown.cs.ivy.mint.MintHandler;
import edu.brown.cs.ivy.mint.MintMessage;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

import org.w3c.dom.Element;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.swing.AbstractAction;
import javax.swing.JPopupMenu;

public final class BaitFactory implements BaitConstants, MintConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private boolean server_running;
private boolean server_started;
private Map<String,ResponseHandler> hdlr_map;

private static BaitFactory the_factory = new BaitFactory();



/********************************************************************************/
/*										*/
/*	Setup methods								*/
/*										*/
/********************************************************************************/

public static void setup()
{
   BoardPluginManager.installResources(BaitFactory.class,"limba",new ResourceFilter());
}



private static final class ResourceFilter implements BoardPluginFilter {

   @Override public boolean accept(String nm) {
      return false;
    }

}	// end of inner class ResSurceFilter




public static void initialize(BudaRoot br)
{
// if (!BumpClient.getBump().getOptionBool("bubbles.useLimba")) return;

   BoardLog.logD("BAIT","USING LIMBA");

   switch (BoardSetup.getSetup().getRunMode()) {
      case NORMAL :
      case CLIENT :
         the_factory.setupCallbacks();
	 break;
      case SERVER :
	 break;
    }

   BaitStarter bs = new BaitStarter(br);
   bs.start();
}


private void setupCallbacks() 
{
   BudaRoot.registerMenuButton("Bubble.AI.Ask Limba",new AskLimbaAction());
   BudaRoot.registerMenuButton("Bubble.AI.Set Limba Properties",new SetupLimbaAction());
   BaleFactory.getFactory().addContextListener(new BaitContexter());
}



public static BaitFactory getFactory()
{
   return the_factory;
}



/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

private BaitFactory()
{
   server_running = false;
   server_started = false;
   hdlr_map = new HashMap<>();

   BoardSetup bs = BoardSetup.getSetup();
   MintControl mc = bs.getMintControl();
   mc.register("<LIMBAREPLY RID='_VAR_0' />",new LimbaHandler());
   mc.register("<LIMBAREPLY DO='PING' />",new PingHandler());

   switch (BoardSetup.getSetup().getRunMode()) {
      case NORMAL :
      case CLIENT :
	 break;
      case SERVER :
	 mc.register("<BAIT TYPE='START' />",new StartHandler());
	 break;
    }
}



/********************************************************************************/
/*										*/
/*	Starting methods							*/
/*										*/
/********************************************************************************/

private void start()
{
   startLimba();
   if (!server_running) return;
   BoardProperties bp = BoardProperties.getProperties("Bait");
   if (bp.getBoolean("Bait.ollama.usecontext")) {
      issueCommand("PROJECT",null,"QUERY",null);
    }
}



//CHECKSTYLE:OFF
private boolean startLimba()
// CHECKSTYLE:ON
{
   BoardSetup bs = BoardSetup.getSetup();
   MintControl mc = bs.getMintControl();
   
   if (BoardSetup.getSetup().getRunMode() == RunMode.CLIENT) {
      MintDefaultReply rply = new MintDefaultReply();
      mc.send("<LIMBA DO='PING' />");
      String rslt = rply.waitForString();
      if (rslt != null) {
         server_running = true;
         server_started = true;
         return true;
       }
    }
   
   if (server_running || server_started) return false;
   
   BoardLog.logD("BAIT","Starting limba server");
   
   IvyExec exec = null;
   File wd =  new File(bs.getDefaultWorkspace());
   File logf = new File(wd,"limba.log");
   
   BoardProperties bp = BoardProperties.getProperties("Bait");
   
   List<String> args = new ArrayList<>();
   
   args.add(IvyExecQuery.getJavaPath());
   
   String dbgargs = bp.getProperty("Bait.jvm.args");
   if (dbgargs != null && dbgargs.contains("###")) {
      int port = (int) Math.random() * 1000 + 3000;
      dbgargs = dbgargs.replace("###",Integer.toString(port));
      BoardLog.logI("BAIT","Limba debug port " + port);
    }
   if (dbgargs != null) {
      StringTokenizer tok = new StringTokenizer(dbgargs);
      while (tok.hasMoreTokens()) {
         args.add(tok.nextToken());
       }
    }
   File jarfile = IvyFile.getJarFile(BaitFactory.class);
   
   String xcp = bp.getProperty("Bait.limba.class.path");
   if (xcp == null) {
      xcp = System.getProperty("java.class.path");
      String ycp = bp.getProperty("Bait.limba.add.path");
      if (ycp != null) xcp = ycp + File.pathSeparator + xcp;
    }
   else {
      BoardSetup setup = BoardSetup.getSetup();
      StringBuffer buf = new StringBuffer();
      StringTokenizer tok = new StringTokenizer(xcp,":;");
      while (tok.hasMoreTokens()) {
	 String elt = tok.nextToken();
	 if (!elt.startsWith("/") &&  !elt.startsWith("\\")) {
	    if (elt.equals("eclipsejar")) {
	       elt = setup.getEclipsePath();
	     }
	    else if (elt.equals("limba.jar") && jarfile != null) {
	       elt = jarfile.getPath();
	     }
	    else {
	       String oelt = elt;
	       elt = setup.getLibraryPath(elt);
	       File f1 = new File(elt);
	       if (!f1.exists()) {
		  f1 = setup.getLibraryDirectory().getParentFile();
		  File f2 = new File(f1,"dropins");
		  File f3 = new File(f2,oelt);
		  if (f3.exists()) elt = f3.getPath();
		}
	       BoardLog.logD("BAIT","Use class path limba element " + elt);
	     }
	  }
	 if (buf.length() > 0) buf.append(File.pathSeparator);
	 buf.append(elt);
       }
      xcp = buf.toString();
    }
   args.add("-cp");
   args.add(xcp);
   
   args.add("edu.brown.cs.limba.limba.LimbaMain");
   args.add("-m");
   args.add(bs.getMintName());
   args.add("-L");
   args.add(logf.getPath());
   if (bp.getBoolean("Bait.limba.debug")) {
      args.add("-D");
    }
   String oh = bp.getProperty("Bait.ollama.host");
   if (oh != null) {
      args.add("-host");
      args.add(oh);
    }
   int op = bp.getInt("Bait.ollama.port");
   if (op > 0) {
      args.add("-port");
      args.add(Integer.toString(op));
    }
   String ah = bp.getProperty("Bait.ollama.althost");
   if (ah != null) {
      args.add("-althost");
      args.add(ah);
    }
   int ap = bp.getInt("Bait.ollama.altport");
   if (ap > 0) {
      args.add("-altport");
      args.add(Integer.toString(ap));
    }
   String mdl = bp.getString("Bait.ollama.model",null);
   if (mdl != null && !mdl.isEmpty()) {
      args.add("-llama");
      args.add(mdl);
    }
            
   synchronized (this) {
      if (server_started || server_running) return false; 
      server_started = true;
    }
   
   for (int i = 0; i < 100; ++i) {
      MintDefaultReply rply = new MintDefaultReply();
      mc.send("<LIMBA DO='PING' />",rply,MINT_MSG_FIRST_NON_NULL);
      String rslt = rply.waitForString(1000);
      BoardLog.logD("BAIT","Limba ping response " + rslt);
      if (rslt != null) {
	 server_running = true;
	 break;
       }
      if (i == 0) {
	 try {
            // make IGNORE_OUTPUT to clean up otuput
            exec = new IvyExec(args,null,IvyExec.ERROR_OUTPUT);    
	    BoardLog.logD("BAIT","Run " + exec.getCommand());
	  }
	 catch (IOException e) {
	    break;
	  }
       }
      else {
	 try {
	    if (exec != null) {
	       int sts = exec.exitValue();
	       BoardLog.logD("BAIT","Limba server disappeared with status " + sts);
	       break;
	     }
	  }
	 catch (IllegalThreadStateException e) { }
       }
      
      try {
	 Thread.sleep(2000);
       }
      catch (InterruptedException e) { }
    }
   if (!server_running) {
      BoardLog.logE("BAIT","Unable to start limba server: " + args);
      return false;
    }
  
   return true;
}


private String getProjectProperty(String prop)
{
   BoardProperties bp = BoardProperties.getProperties("Bait");
   BoardSetup bs = BoardSetup.getSetup();
   String ws = bs.getDefaultWorkspace();
   int idx = ws.lastIndexOf(".");
   if (idx > 0) ws = ws.substring(idx+1);
   String p1 = bp.getProperty(prop + "." + ws);
   if (p1 != null) return p1;
   String p2 = bp.getProperty(prop);
   return p2;
}




private static class BaitStarter extends Thread {

   BaitStarter(BudaRoot br) {
      super("Limba Starter");
    }

   @Override public void run() {
      the_factory.start();
    }

}	// end of inner class BaitStarter


private final class StartHandler implements MintHandler {

   @Override public void receive(MintMessage msg,MintArguments args) {
      boolean sts = startLimba();
      if (sts) {
        String sty = getProjectProperty("Bait.ollama.style");
        if (sty != null) {
           sendLimbaMessage("STYLE",null,sty);
         }
        String ctx = getProjectProperty("Bait.ollama.context");
        if (ctx != null) {
           sendLimbaMessage("CONTEXT",null,ctx);
         }
       }
      if (sts) msg.replyTo("<RESULT VALUE='true'/>");
      else msg.replyTo("<RESULT VALUE='false' />");
    }

}	 // end of inner class StartHandler



/********************************************************************************/
/*                                                                              */
/*      Command methods                                                         */
/*                                                                              */
/********************************************************************************/

void issueCommand(String cmd,CommandArgs args,String body,ResponseHandler hdlr)
{
   IvyXmlWriter xw = new IvyXmlWriter();
   xw.cdataElement("BODY",body);
   String cnts = xw.toString();
   xw.close();
   issueXmlCommand(cmd,args,cnts,hdlr);
}


void issueXmlCommand(String cmd,CommandArgs args,String body,ResponseHandler hdlr)
{
   if (hdlr == null) hdlr = new DummyResponder(); 
   
   String rid = "LIMBA_" + (int) (Math.random()*1000000);
   hdlr_map.put(rid,hdlr);
   if (args == null) args = new CommandArgs("RID",rid);
   else args.put("RID",rid);
   
   Element xml = sendLimbaMessage(cmd,args,body);
   String nrid = IvyXml.getAttrString(xml,"RID");
   if (!rid.equals(nrid)) {
      BoardLog.logE("BAIT","Reply ids don't match " + rid + " " + nrid);
    }
}


private static final class DummyResponder implements ResponseHandler {
   @Override public void handleResponse(Element xml) { }
}       // end of inner class DummyResponder



/********************************************************************************/
/*										*/
/*	Limba Server communication						*/
/*										*/
/********************************************************************************/

Element sendLimbaMessage(String cmd,CommandArgs args,String cnts)
{
   if (!server_running) return null;

   BoardSetup bs = BoardSetup.getSetup();
   MintControl mc = bs.getMintControl();

   MintDefaultReply rply = new MintDefaultReply();
   IvyXmlWriter xw = new IvyXmlWriter();
   xw.begin("LIMBA");
   xw.field("DO",cmd);
   if (args != null) {
      for (Map.Entry<String,Object> ent : args.entrySet()) {
	 xw.field(ent.getKey(),ent.getValue());
       }
    }
   if (cnts != null) {
      if (!cnts.startsWith("<")) {
         cnts = "<BODY>" + cnts + "</BODY>";
       }
      xw.xmlText(cnts);
    }
   xw.end("LIMBA");
   String msg = xw.toString();
   xw.close();

   BoardLog.logD("BAIT","Send to LIMBA: " + msg);
   
   mc.send(msg,rply,MINT_MSG_FIRST_NON_NULL);
   
   Element rslt = rply.waitForXml(60000);
   
   BoardLog.logD("BSEAN","Reply from LIMBA: " + IvyXml.convertXmlToString(rslt));
   
   return rslt;
}


/********************************************************************************/
/*										*/
/*	Message handling							*/
/*										*/
/********************************************************************************/

private final class LimbaHandler implements MintHandler {

   @Override public void receive(MintMessage msg,MintArguments args) {
      Element xml = msg.getXml();
      String rid = args.getArgument(0);
      String rslt = null;
      try {
         BoardLog.logD("BAIT","Handle deferred reply " + rid + " " +
               IvyXml.convertXmlToString(xml));
         ResponseHandler hdlr = hdlr_map.remove(rid);
         if (hdlr != null) {
            Element xmlrslt = IvyXml.getChild(xml,"RESULT");
            hdlr.handleResponse(xmlrslt);
          }
       }
      catch (Throwable e) {
         BoardLog.logE("BAIT","Error processing command",e);
       }
      msg.replyTo(rslt);
   }

}	// end of inner class UpdateHandler



private final class PingHandler implements MintHandler {

   @Override public void receive(MintMessage msg,MintArguments args) {
      msg.replyTo("<PONG/>");
    }

}	// end of inner class UpdateHandler



/********************************************************************************/
/*                                                                              */
/*      Action methods                                                          */
/*                                                                              */
/********************************************************************************/

private boolean createGenerateBubble(BaleContextConfig cfg,boolean mthd,boolean test)
{
   String mnm = cfg.getFullName(); 
   if (mnm == null) return false;
   BumpLocation loc = null;
   if (mthd) {
      List<BumpLocation> locs = BumpClient.getBump().findMethod(null,mnm,false);
      if (locs == null || locs.size() == 0) return false;
      loc = locs.get(0);
    }
   else {
      List<BumpLocation> locs = BumpClient.getBump().findClassDefinition(null,mnm);
      if (locs == null || locs.size() == 0) return false;
      loc = locs.get(0);
    }
   
   BudaBubble bb = new BaitGenerateBubble(loc,cfg.getEditor(),mthd,test); 
   
   BudaBubbleArea bba = BudaRoot.findBudaBubbleArea(cfg.getEditor());
   if (bba == null) return false;
   bba.addBubble(bb,cfg.getEditor(),null,
         BudaConstants.PLACEMENT_PREFER|BudaConstants.PLACEMENT_MOVETO|BudaConstants.PLACEMENT_NEW);
   
   return true;
}


private boolean createJavadocHandler(BaleContextConfig cfg)
{
   String mnm = cfg.getMethodName();
   if (mnm == null) return false;
   
   List<BumpLocation> locs = BumpClient.getBump().findMethod(null,mnm,false);
   if (locs == null || locs.size() == 0) return false;
   BumpLocation loc = null;
   for (BumpLocation bloc : locs) {
      int st = bloc.getDefinitionOffset();
      int ed = bloc.getDefinitionEndOffset();
      if (cfg.getDocumentOffset() >= st && cfg.getDocumentOffset() <= ed) {
         loc = bloc;
         break;
       }
    }
   if (loc == null) return false;
   BaitJavaDocer bjd = new BaitJavaDocer(loc);
   bjd.process(); 
   return true;
}


private boolean createTestCasesHandler(BaleContextConfig cfg)
{
   String mnm = cfg.getMethodName();
   if (mnm == null) return false;
   
   List<BumpLocation> locs = BumpClient.getBump().findMethod(null,mnm,false);
   if (locs == null || locs.size() == 0) return false;
   BumpLocation loc = locs.get(0);
   IvyLog.logD("LIMBA","Create test cases for " + loc);
// BaitJavaDocer bjd = new BaitJavaDocer(loc);
// bjd.process(); 
   return true;
}





/********************************************************************************/
/*                                                                              */
/*      Action classes                                                          */
/*                                                                              */
/********************************************************************************/

private final class AskLimbaAction implements BudaConstants.ButtonListener {
   
   @Override public void buttonActivated(BudaBubbleArea bba,String id,Point pt) {
      BaitChatBubble bbl = new BaitChatBubble();
      BoardLog.logD("BAIT","Create chat bubble " + bbl);
      bba.addBubble(bbl,BudaBubblePosition.USERPOS,pt.x,pt.y);
    }
   
}       // end of inner class AskLimbaAction


private final class SetupLimbaAction implements BudaConstants.ButtonListener {

   @Override public void buttonActivated(BudaBubbleArea bba,String id,Point pt) {
      BaitSetupBubble bbl = new BaitSetupBubble(); 
      BoardLog.logD("BAIT","Create setup bubble " + bbl);
      bba.addBubble(bbl,BudaBubblePosition.USERPOS,pt.x,pt.y);
    }

}       // end of inner class SetupLimbaAction


private class GenerateTestMethodAction extends AbstractAction {
   
   private transient BaleContextConfig start_config;
   private static final long serialVersionUID = 1;
   
   GenerateTestMethodAction(BaleContextConfig cfg) {
      super("Generate and Test Method Implementation");
      start_config = cfg;
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      createGenerateBubble(start_config,true,true);
    }
   
}       // end of inner class GenerateTestMethodAction


private class GenerateTestClassAction extends AbstractAction {

   private transient BaleContextConfig start_config;
   private static final long serialVersionUID = 1;
   
   GenerateTestClassAction(BaleContextConfig cfg) {
      super("Generate and Test Class Implementation");
      start_config = cfg;
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      createGenerateBubble(start_config,false,true);
    }
   
}       // end of inner class GenerateTestClassAction


private class GenerateMethodAction extends AbstractAction {
   
   private transient BaleContextConfig start_config;
   private static final long serialVersionUID = 1;
   
   GenerateMethodAction(BaleContextConfig cfg) {
      super("Generate Method Implementation");
      start_config = cfg;
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      createGenerateBubble(start_config,true,false);
    }

}       // end of inner class GenerateMethodAction



private class GenerateClassAction extends AbstractAction {
   
   private transient BaleContextConfig start_config;
   private static final long serialVersionUID = 1;
   
   GenerateClassAction(BaleContextConfig cfg) {
      super("Generate Class Implementation");
      start_config = cfg;
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      createGenerateBubble(start_config,false,false);
    }

}       // end of inner class GenerateClassAction



private class JavadocMethodAction extends AbstractAction {
   
   private transient BaleContextConfig start_config;
   private static final long serialVersionUID = 1;
   
   JavadocMethodAction(BaleContextConfig cfg) {
      super("Generate JavaDoc for Method");
      start_config = cfg;
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      createJavadocHandler(start_config);
    }
   
}       // end of inner class JavadocMethodAction



private class JavadocClassAction extends AbstractAction {
   
   private transient BaleContextConfig start_config;
   private static final long serialVersionUID = 1;
   
   JavadocClassAction(BaleContextConfig cfg) {
      super("Generate JavaDoc for Class");
      start_config = cfg;
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      createJavadocHandler(start_config);
    }

}       // end of inner class JavadocClassAction



private class TestCaseMethodAction extends AbstractAction {
   
   private transient BaleContextConfig start_config;
   private static final long serialVersionUID = 1;
   
   TestCaseMethodAction(BaleContextConfig cfg) {
      super("Generate Test Cases for Method");
      start_config = cfg;
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      createTestCasesHandler(start_config);
    }

}       // end of inner class TestCaseMethodAction



private class TestCaseClassAction extends AbstractAction {
   
   private transient BaleContextConfig start_config;
   private static final long serialVersionUID = 1;
   
   TestCaseClassAction(BaleContextConfig cfg) {
      super("Generate Test Cases for Class");
      start_config = cfg;
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      createTestCasesHandler(start_config);
    }
   
}       // end of inner class TestCaseClassction





/********************************************************************************/
/*                                                                              */
/*      Editor context actions                                                  */
/*                                                                              */
/********************************************************************************/

private final class BaitContexter implements BaleConstants.BaleContextListener {
   
   @Override public void addPopupMenuItems(BaleContextConfig cfg,JPopupMenu menu) {
      switch (cfg.getTokenType()) {
         case METHOD_DECL_ID :
            menu.add(new GenerateTestMethodAction(cfg));
            menu.add(new GenerateMethodAction(cfg));
            menu.add(new JavadocMethodAction(cfg));
            menu.add(new TestCaseMethodAction(cfg));
            break;
         case CLASS_DECL_ID :
         case TYPE_ID :
            menu.add(new GenerateTestClassAction(cfg));
            menu.add(new GenerateClassAction(cfg));
            menu.add(new JavadocClassAction(cfg));
            menu.add(new TestCaseClassAction(cfg));
            break;
            
         default :
            break;
       }
    }

}	// end of inner class BucsContexter






}      // end of class BaitFactory




/* end of BaitFactory.java */

