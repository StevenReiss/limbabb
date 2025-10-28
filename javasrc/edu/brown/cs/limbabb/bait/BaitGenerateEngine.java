/********************************************************************************/
/*										*/
/*		BaitGenerateEngine.java 					*/
/*										*/
/*	Handle setup with test cases, etc. for calling LIMBA						       */
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

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import javax.imageio.ImageIO;
import javax.swing.text.BadLocationException;
import javax.swing.text.Segment;

import org.w3c.dom.Element;

import edu.brown.cs.bubbles.bale.BaleFactory;
import edu.brown.cs.bubbles.bale.BaleConstants.BaleFileOverview;
import edu.brown.cs.bubbles.batt.BattConstants.BattCallTest;
import edu.brown.cs.bubbles.batt.BattConstants.BattTest;
import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.board.BoardProperties;
import edu.brown.cs.bubbles.board.BoardThreadPool;
import edu.brown.cs.bubbles.bump.BumpClient;
import edu.brown.cs.bubbles.bump.BumpLocation;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class BaitGenerateEngine implements BaitConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private BumpLocation	bump_location;
private boolean 	context_flag;
private String		generate_description;
private List<BattCallTest> test_cases;
private List<BattTest>	user_tests;
private List<BaitUserFile> data_files;
private String		test_code;
private String          code_description;



/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

BaitGenerateEngine(BumpLocation loc)
{
   bump_location = loc;
   generate_description = null;
   test_cases = null;
   user_tests = null;
   test_code = null;
   code_description = null;
   data_files = null;
   context_flag = false;
}


/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

void setDescription(String d)			{ generate_description = d; }

void setTestCases(List<BattCallTest> t) 	{ test_cases = t; }

void setTestCode(String cd,String desc)			
{ 
   test_code = cd; 
   code_description = desc;
}

void setUserTest(List<BattTest> t)		{ user_tests = t; }

void setDataFiles(Collection<BaitUserFile> fl)
{
   data_files = new ArrayList<BaitUserFile>(fl);
}




/********************************************************************************/
/*										*/
/*	Context methods 							*/
/*										*/
/********************************************************************************/

void setContextFlag(boolean fg) 		{ context_flag = fg; }


private void outputFile(IvyXmlWriter xw,String key,File f)
{
   StringBuffer sbuf = new StringBuffer();
   byte [] buf = new byte[8192];
   try (FileInputStream fis = new FileInputStream(f)) {
      for ( ; ; ) {
	 int rln = fis.read(buf);
	 if (rln <= 0) break;
	 for (int i = 0; i < rln; ++i) {
	    int v = buf[i] & 0xff;
	    String s1 = Integer.toHexString(v);
	    if (s1.length() == 1) sbuf.append("0");
	    sbuf.append(s1);
	    if ((i%32) == 31) sbuf.append("\n");
	  }
       }
    }
   catch (IOException e) {
      return;
    }

   xw.begin(key);
   xw.field("LENGTH",f.length());
   String fnm = f.getName();
   xw.field("NAME",fnm);
   int idx = fnm.lastIndexOf(".");
   if (idx > 0) {
      xw.field("EXTENSION",fnm.substring(idx));
    }
   xw.cdataElement("CONTENTS",sbuf.toString());
   xw.end(key);
}


/********************************************************************************/
/*										*/
/*	Basic Search methods							*/
/*										*/
/********************************************************************************/

void startSearch(BaitGenerateRequest sr)
{
   SearchRunner searcher = new SearchRunner(sr,createGenerateRequest());

   BoardThreadPool.start(searcher);
}



private String createGenerateRequest()
{											
   String msgn = checkSignature();
   String methodname = bump_location.getSymbolName();
   String pfx = null;
   int idx = methodname.lastIndexOf(".");
   if (idx > 0) {
      pfx = methodname.substring(0,idx);
      methodname = methodname.substring(idx+1);
    }
    
   BoardProperties bp = BoardProperties.getProperties("Bait");
   
   IvyXmlWriter xw = new IvyXmlWriter();
   xw.begin("SEARCH");
   xw.field("WHAT","METHOD");
   xw.field("NAME",methodname);
   if (pfx != null) xw.field("PREFIX",pfx);
   xw.field("LANGUAGE","JAVA");
   xw.field("USECONTEXT",context_flag);
   xw.textElement("SIGNATURE",msgn);
   xw.cdataElement("DESCRIPTION",generate_description);
   if (bp.getBoolean("Bait.remote.access")) xw.field("REMOTE",true);

   xw.begin("TESTS");
   int ctr = 0;
   if (test_cases != null) {
      for (BattCallTest bct : test_cases) {
	 xw.begin("TESTCASE");
	 xw.field("NAME","TEST_" + (++ctr));
	 xw.field("TYPE","CALLS");
         xw.textElement("DESCRIPTION",bct.getTestDescription());          
	 xw.begin("CALL");
	 xw.field("METHOD",methodname);
	 xw.field("OP",bct.getTestOpName());
	 xw.begin("INPUT");
	 xw.cdataElement("VALUE",bct.getTestInput());
	 xw.end("INPUT");
	 xw.begin("OUTPUT");
	 xw.cdataElement("VALUE",bct.getTestOutput());
	 xw.end("OUTPUT");
	 xw.end("CALL");
	 xw.end("TESTCASE");
       }
    }
   if (test_code != null) {
      xw.begin("TESTCASE");
      xw.field("NAME", "TEST_" + (++ctr));
      xw.field("TYPE","USERCODE");
      xw.textElement("DESCRIPTION",code_description);
      xw.cdataElement("CODE",test_code);
      xw.end("TESTCASE");
    }
   if (user_tests != null) {
      for (BattTest bt : user_tests) {
	 xw.begin("TESTCASE");
	 xw.field("NAME",bt.getName());
	 xw.field("TYPE","JUNIT");
	 xw.field("CLASS",bt.getClassName());
	 xw.field("METHOD",bt.getMethodName());
	 xw.field("TESTNAME",bt.getMethodName() + "(" + bt.getClassName() + ")");
         xw.textElement("DESCRIPTION","Test " + bt.getName());
	 xw.end("TESTCASE");
       }
    }
   xw.end("TESTS");

   setupSearchContext(xw);

   xw.end("SEARCH");

   String rslt = xw.toString();
   xw.close();

   return rslt;
}


void setupSearchContext(IvyXmlWriter xw)
{
   BoardLog.logD("BAIT","Creating context");
   
   BoardProperties bp = BoardProperties.getProperties("Bait");
   
   Element e = BumpClient.getBump().getProjectData(bump_location.getProject(),
	 false,true,false,false,false);
   if (e == null) {
      BoardLog.logD("BAIT","No project data available for context for " + bump_location.getProject());
      return;
    }
   
   List<File> classpaths = new ArrayList<File>();
   Element cpth = IvyXml.getChild(e,"CLASSPATH");
   
   for (Element pe : IvyXml.children(cpth,"PATH")) {
      String typ = IvyXml.getAttrString(pe,"TYPE");
      if (typ.equals("SOURCE")) continue;
      String onm = IvyXml.getTextElement(pe,"BINARY");
      if (onm == null) onm = IvyXml.getTextElement(pe,"OUTPUT");
      if (onm == null) continue;
      
      // skip standard java libraries
      if (onm.contains("/jdk") || onm.contains("\\jdk") ||
            onm.contains("/jrt-fs") || onm.contains("\\jrt-fs") ||
            onm.contains("/jre") || onm.contains("\\jre")) continue;
      if (onm.contains("JavaVirtualMachines")) continue;
      if (onm.contains("-openjdk-")) continue;
      if (onm.startsWith("/System/Library/Java")) continue;
      if (onm.endsWith("/junit.jar") || onm.endsWith("\\junit.jar")) continue;
      if (onm.endsWith("/poppy.jar") || onm.endsWith("\\poppy.jar")) continue;
      if (onm.contains("/eclipse/plugins/org.") || onm.contains("\\eclipse\\plugins\\org.")) continue;
      BoardLog.logD("BAIT","Add context library " + onm);
      
      Element acc = IvyXml.getChild(pe,"ACCESS");
      if (acc != null) continue;
      
      File f = new File(onm);
      if (f.exists()) classpaths.add(f);
    }
   
   File tnm = null;
   if (bp.getBoolean("Bait.remote.access")) {
      Manifest manifest = null;
      for (File f : classpaths) manifest = handleManifest(f,manifest);
      try {
	 tnm = File.createTempFile("baitcontext","jar");
	 OutputStream ost = new BufferedOutputStream(new FileOutputStream(tnm));
	 JarOutputStream jst = null;
	 if (manifest == null) jst = new JarOutputStream(ost);
	 else jst = new JarOutputStream(ost,manifest);
         
	 for (File f : classpaths) addToClassContext(f,jst);
	 jst.close();
       }
      catch (IOException ex) {
	 BoardLog.logE("BAIT","Problem creating context jar file",ex);
	 return;
       }
    }
   
   xw.begin("CONTEXT");
   
   startContextFile(xw);
   
   File src = bump_location.getFile();
   if (bp.getBoolean("Bait.remote.access")) {
      xw.field("REMOTE",true);
      if (src != null && src.exists()) {
	 outputFile(xw,"SOURCE",src);
       }
      if (data_files != null) {
	 for (BaitUserFile uf : data_files) {
	    outputFile(xw,"DATA",uf.getFile());
	  }
       }
      if (tnm != null) {
	 outputFile(xw,"CONTEXTJAR",tnm);
       }
    }
   else {
      if (src != null && src.exists()) {
	 xw.begin("SOURCE");
	 xw.field("NAME",src.getPath());
	 xw.end("SOURCE");
       }
      if (data_files != null) {
	 for (BaitUserFile uf : data_files) {
	    xw.begin("DATA");
	    xw.field("NAME",uf.getFile().getPath());
	    xw.end("DATA");
	  }
       }
      for (File f : classpaths) {
	 xw.textElement("CLASSPATH",f.getPath());
       }
    }
   
   xw.end("CONTEXT");
}





private class SearchRunner implements Runnable, ResponseHandler {

   private String search_request;
   private BaitGenerateRequest search_callback;

   SearchRunner(BaitGenerateRequest sr,String rq) {
      search_callback = sr;
      search_request = rq;
    }

   @Override public void run() {
      if (search_request == null) {
         search_callback.handleGenerateFailed();
         return;
       }
      String what = "METHOD";
      switch (bump_location.getSymbolType()) {
         case ANNOTATION :
         case CLASS :
         case ENUM :
         case INTERFACE :
         case THROWABLE :
            what = "CLASS";
            break;
         case CONSTRUCTOR :
         case FUNCTION :
         case STATIC_INITIALIZER :
             what = "METHOD";
             break;
         case FIELD :
         case ENUM_CONSTANT :
            what = "FIELD";
            break;
       }
      CommandArgs args = new CommandArgs("TYPE",what);
      BaitFactory bf = BaitFactory.getFactory();
      bf.issueXmlCommand("FIND",args,search_request,this);  
    }
   
   @Override public void handleResponse(Element rslt) {
      List<BaitGenerateResult> rslts = new ArrayList<>();
      Element sols = IvyXml.getChild(rslt,"SOLUTIONS");
      for (Element sol : IvyXml.children(sols,"SOLUTION")) {
         LimbaGenerateResult sr = new LimbaGenerateResult(sol);
         rslts.add(sr);
       }
      
      List<BaitGenerateInput> irslt = new ArrayList<BaitGenerateInput>();
      Element inps = IvyXml.getChild(rslt,"USERINPUT");
      Element test = IvyXml.getChild(inps,"TESTCASE");
      for (Element uc : IvyXml.children(test,"USERCASE")) {
         LimbaUIResult sr = new LimbaUIResult(uc);
         irslt.add(sr);
       }
      
      if (rslts.size() > 0) search_callback.handleGenerateSucceeded(rslts);
      else if (irslt.size() > 0) search_callback.handleGenerateInputs(irslt);
      else search_callback.handleGenerateFailed();
    }

}	// end of inner class SearchRunner



private String checkSignature()
{
   String snm = bump_location.getSymbolName();
   String pnm = bump_location.getParameters();
   if (pnm == null) pnm = "()";
   String pfx = "";
   if (Modifier.isStatic(bump_location.getModifiers())) pfx = "static ";
   int idx = snm.lastIndexOf(".");
   if (idx > 0) snm = snm.substring(idx+1);
   String ret = bump_location.getReturnType();

   String sgn = pfx + ret + " " + snm + pnm;

   return sgn;
}







/********************************************************************************/
/*										*/
/*	Context Creation Helper methods 					*/
/*										*/
/********************************************************************************/

private void startContextFile(IvyXmlWriter xw)
{
   xw.field("LANGUAGE","JAVA");
   xw.field("USEPATH",true);
   xw.field("SEPARATOR",File.separator);

   String mnm = bump_location.getSymbolName();
   int idx = mnm.lastIndexOf(".");
   String cnm = null;
   String pnm = null;
   if (idx > 0) {
      cnm = mnm.substring(0,idx);
      mnm = mnm.substring(idx+1);
      idx = cnm.lastIndexOf(".");
      if (idx > 0) {
	 pnm = cnm.substring(0,idx);
	 cnm = cnm.substring(idx+1);
       }
    }
   if (pnm != null) xw.field("PACKAGE",pnm);
   if (cnm != null) xw.field("CLASS",cnm);

   BumpClient bc = BumpClient.getBump();
   List<BumpLocation>  imps = bc.findClassHeader(bump_location.getProject(),
         bump_location.getFile(),null,false,true);
   BaleFileOverview bf = BaleFactory.getFactory().getFileOverview(
         bump_location.getProject(),
         bump_location.getFile());
   int maxp = 0;
   for (BumpLocation imploc : imps) {
      int epos = bf.mapOffsetToJava(imploc.getEndOffset());
      if (epos > maxp) maxp = epos;
    }
   Segment s = new Segment();
   try {
      if (maxp > 0) {
         bf.getText(0,maxp,s);
       }
    }
   catch (BadLocationException e) { }
   for (BumpLocation imploc : imps) {
      int spos = bf.mapOffsetToJava(imploc.getOffset());
      int epos = bf.mapOffsetToJava(imploc.getEndOffset());
      if (spos < 0) continue;
      String text = s.subSequence(spos,epos).toString().trim();
      xw.textElement("IMPORT",text);
    }

   // output imports ?

   if (data_files != null) {
      for (BaitUserFile uf : data_files) uf.addEntry(xw);
    }
}




private Manifest handleManifest(File f,Manifest m)
{
   if (!f.exists() || !f.canRead()) return m;
   if (f.isDirectory()) return m;

   try {
      JarFile jf = new JarFile(f);
      Manifest m1 = jf.getManifest();
      if (m1 != null) m = mergeManifest(m1,m);
      jf.close();
    }
   catch (IOException e) {
      BoardLog.logE("BAIT","Java file must be a directory or a jar file");
    }

   return m;
}



private Manifest mergeManifest(Manifest m0,Manifest m1)
{
   if (m0 == null) return m1;
   if (m1 == null) m1 = new Manifest();

   Attributes na = m0.getMainAttributes();
   Attributes a = m1.getMainAttributes();
   a.putAll(na);
   Map<String,Attributes> nm = m0.getEntries();
   Map<String,Attributes> mm = m1.getEntries();
   for (Map.Entry<String,Attributes> ent : nm.entrySet()) {
      Attributes ma = mm.get(ent.getKey());
      if (ma == null) mm.put(ent.getKey(),ent.getValue());
      else ma.putAll(ent.getValue());
    }

   return m1;
}



private void addToClassContext(File f,JarOutputStream jst) throws IOException
{
   if (!f.exists() || !f.canRead()) return;
   if (f.isDirectory()) {
      addDirectoryClassFiles(f,f.getPath(),jst);
    }
   else {
      JarFile jf = new JarFile(f);
      addJarFile(jf,jst);
    }
}



private void addDirectoryClassFiles(File dir,String pfx,JarOutputStream jst) throws IOException
{
   if (dir.isDirectory()) {
      File [] dirf = dir.listFiles();
      if (dirf != null) {
	 for (File f : dirf) addDirectoryClassFiles(f,pfx,jst);
       }
    }
   else if (dir.getPath().endsWith(".class")) addSimpleFile(dir,pfx,jst);
}



private void addSimpleFile(File f,String pfx,JarOutputStream jst) throws IOException
{
   String x = f.getPath();
   if (pfx != null && x.startsWith(pfx)) {
      int i = pfx.length();
      x = x.substring(i);
      if (x.startsWith(File.separator)) x = x.substring(1);
    }

   addToJarFile(f,x,jst);
}



private void addJarFile(JarFile jf,JarOutputStream jst) throws IOException
{
   for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
      ZipEntry je = e.nextElement();
      if (je.getName().equals("META-INF/MANIFEST.MF")) continue;

      BufferedInputStream ins = new BufferedInputStream(jf.getInputStream(je));
      addToJarFile(ins,je.getName(),jst);
    }
}






private void addToJarFile(File f,String jnm,JarOutputStream jst) throws IOException
{
   if (!f.exists() || !f.canRead()) return;
   BufferedInputStream ins = new BufferedInputStream(new FileInputStream(f));

   addToJarFile(ins,jnm,jst);
}



private void addToJarFile(InputStream ins,String jnm,JarOutputStream jst) throws IOException
{
   byte [] buf = new byte[16384];

   ZipEntry ze = new ZipEntry(jnm);
   try {
      jst.putNextEntry(ze);
    }
   catch (ZipException ex) {
      ins.close();
      return;
    }

   for ( ; ; ) {
      int ln = ins.read(buf);
      if (ln <= 0) break;
      jst.write(buf,0,ln);
    }
   ins.close();
   jst.flush();
   jst.closeEntry();
}



/********************************************************************************/
/*										*/
/*	Search result holder							*/
/*										*/
/********************************************************************************/

private static class LimbaGenerateResult implements BaitGenerateResult { 

   private String result_name;
   private String result_code;
   private int	  result_lines;
   private int	  result_size;
   private double result_score;

   LimbaGenerateResult(Element xml) {
      result_name = IvyXml.getTextElement(xml,"NAME");
      result_code = IvyXml.getTextElement(xml,"CODE");
      Element comp = IvyXml.getChild(xml,"COMPLEXITY");
      result_lines = IvyXml.getAttrInt(comp,"LINES");
      result_size = IvyXml.getAttrInt(comp,"CODE");
      result_score = IvyXml.getAttrInt(comp,"SCORE");
    }

   @Override public String getResultName()	{ return result_name; }
   @Override public String getCode()		{ return result_code; }
   @Override public int getNumLines()		{ return result_lines; }
   @Override public int getCodeSize()		{ return result_size; }
   @Override public double getScore()           { return result_score; } 

}	// end of inner class SearchResult



private static class LimbaUIResult implements BaitGenerateInput {

   private BufferedImage user_image;

   LimbaUIResult(Element xml) {
      user_image = null;
      String imghtml = IvyXml.getTextElement(xml,"VALUE");
      Element x1 = IvyXml.convertStringToXml(imghtml);		// <IMG ...></IMG>
      String src = IvyXml.getAttrString(x1,"SRC");
      int idx = src.indexOf(",");
      src = src.substring(idx+1);
      byte [] img = Base64.getDecoder().decode(src);
      ByteArrayInputStream bas = new ByteArrayInputStream(img);
      try {
         user_image = ImageIO.read(bas);
       }
      catch (IOException e) {
         BoardLog.logE("BAIT","Problem converting image",e);
       }
   
      // jar_string = IvyXml.getTextElement(xml,"RUNJAR");
    }

   @Override public BufferedImage getImage()	{ return user_image; }

}	// end of inner class S6UIResult



}	// end of class BaitGenerateEngine




/* end of BaitGenerateEngine.java */

