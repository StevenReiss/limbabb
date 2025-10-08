/********************************************************************************/
/*										*/
/*		BaitConstants.java						*/
/*										*/
/*	Bubbles LIMba Environment external constants				*/
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
import java.util.List;

import org.w3c.dom.Element;

public interface BaitConstants
{

enum TestChoice {
   INPUT_OUTPUT,
   USER_TEST,
   TEST_CASES,
}

enum UserFileType {
   READ,
   WRITE, 
   DIRECTORY
}


interface ResponseHandler {
   void handleResponse(Element xml);
}

interface BaitGenerateResult {
   String getResultName();
   String getCode();
   int getNumLines();
   int getCodeSize();
}

interface BaitGenerateInput {
   BufferedImage getImage();
}  

interface BaitGenerateRequest {
   void handleGenerateFailed();
   void handleGenerateSucceeded(List<BaitGenerateResult> result);
   void handleGenerateInputs(List<BaitGenerateInput> result);
}



}	// end of interface BaitConstants




/* end of BaitConstants.java */

