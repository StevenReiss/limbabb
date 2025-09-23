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
import edu.brown.cs.bubbles.buda.BudaBubbleArea;
import edu.brown.cs.bubbles.buda.BudaConstants;
import edu.brown.cs.bubbles.buda.BudaRoot;
import edu.brown.cs.bubbles.buda.BudaConstants.BudaBubblePosition;
import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.ivy.exec.IvyExecQuery;
import edu.brown.cs.ivy.file.IvyFile;
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
import java.net.InetSocketAddress;
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
   if (!server_running) server_started = false; 		// for debug
   startLimba();
   if (!server_running) return;
}



//CHECKSTYLE:OFF
private boolean startLimba()
// CHECKSTYLE:ON
{
   BoardSetup bs = BoardSetup.getSetup();
   MintControl mc = bs.getMintControl();
   InetSocketAddress addr = mc.getSocketAddress();
   
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
   
   String host = bp.getProperty("Bait.run.host");
   if (host == null) {
      args.add(IvyExecQuery.getJavaPath());
    }
   else {
      args.add(BoardSetup.getSetup().getBinaryPath("startlimba"));
      args.add(host);
      args.add("-Dedu.brown.cs.ivy.mint.server.host=" + addr.getHostName());
      args.add("-Dedu.brown.cs.ivy.mint.server.post=" + addr.getPort());
    }
   
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
   String mdl = bp.getString("Bait.ollama.model",null);
   if (mdl != null && !mdl.isEmpty()) {
      args.add("-llama");
      args.add(mdl);
    }
   if (host != null && bp.getBoolean("Bait.remote.access")) {
      args.add("-remote");
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
      return true;
    }
  
   return false;
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
      if (sts) msg.replyTo("<RESULT VALUE='true'/>");
      else msg.replyTo("<RESULT VALUE='false' />");
    }

}	 // end of inner class StartHandler



/********************************************************************************/
/*                                                                              */
/*      Command methods                                                         */
/*                                                                              */
/********************************************************************************/

void issueCommand(String cmd,String body,ResponseHandler hdlr)
{
   String rid = "LIMBA_" + (int) (Math.random()*1000000);
   hdlr_map.put(rid,hdlr);
   CommandArgs args = new CommandArgs("RID",rid);
   IvyXmlWriter xw = new IvyXmlWriter();
   xw.cdataElement("BODY",body);
   Element xml = sendLimbaMessage(cmd,args,xw.toString());
   xw.close();
   String nrid = IvyXml.getAttrString(xml,"RID");
   if (!rid.equals(nrid)) {
      BoardLog.logE("BAIT","Reply ids don't match " + rid + " " + nrid);
    }
   
}



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
      xw.xmlText(cnts);
    }
   xw.end("LIMBA");
   String msg = xw.toString();
   xw.close();

   BoardLog.logD("BAIT","Send to LIMBA: " + msg);
   
   mc.send(msg,rply,MINT_MSG_FIRST_NON_NULL);
   
   Element rslt = rply.waitForXml(60000);
   
   BoardLog.logD("BSEAN","Reply from FAIT: " + IvyXml.convertXmlToString(rslt));
   
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
         if (hdlr != null) hdlr.handleResponse(xml);
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

private final class AskLimbaAction implements BudaConstants.ButtonListener {
   
   @Override public void buttonActivated(BudaBubbleArea bba,String id,Point pt) {
      BaitChatBubble bbl = new BaitChatBubble();
      BoardLog.logD("BAIT","Create chat bubble " + bbl);
      bba.addBubble(bbl,BudaBubblePosition.USERPOS,pt.x,pt.y);
    }
   
}       // end of inner class AskLimbaAction


private final class SetupLimbaAction implements BudaConstants.ButtonListener {

   @Override public void buttonActivated(BudaBubbleArea bba,String id,Point pt) {
      // create limba porperties bubble
    }

}       // end of inner class SetupLimbaAction


private class GenerateTestAction extends AbstractAction {
   
   private transient BaleContextConfig start_config;
   private static final long serialVersionUID = 1;
   
   GenerateTestAction(BaleContextConfig cfg) {
      super("Generate and Test Method Implementation");
      start_config = cfg;
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      // create test case bubble
    }
   
}       // end of inner class GenerateTestAction


private class GenerateAction extends AbstractAction {
   
   private transient BaleContextConfig start_config;
   private static final long serialVersionUID = 1;
   
   GenerateAction(BaleContextConfig cfg) {
      super("Generate Method Implementation");
      start_config = cfg;
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      // generate code for method using javadoc, etc. as prompt
    }

}       // end of inner class GenerateAction



private class JavadocAction extends AbstractAction {
   
   private transient BaleContextConfig start_config;
   private static final long serialVersionUID = 1;
   
   JavadocAction(BaleContextConfig cfg) {
      super("Generate JavaDoc for Method");
      start_config = cfg;
    }
   
   @Override public void actionPerformed(ActionEvent e) {
      // generate javadoc for method
    }
   
}       // end of inner class JavadocAction




/********************************************************************************/
/*                                                                              */
/*      Editor context actions                                                  */
/*                                                                              */
/********************************************************************************/

private final class BaitContexter implements BaleConstants.BaleContextListener {
   
   @Override public void addPopupMenuItems(BaleContextConfig cfg,JPopupMenu menu) {
      switch (cfg.getTokenType()) {
         case METHOD_DECL_ID :
            menu.add(new GenerateTestAction(cfg));
            menu.add(new GenerateAction(cfg));
            menu.add(new JavadocAction(cfg));
            break;
         default :
            break;
       }
    }

}	// end of inner class BucsContexter






}      // end of class BaitFactory




/* end of BaitFactory.java */

