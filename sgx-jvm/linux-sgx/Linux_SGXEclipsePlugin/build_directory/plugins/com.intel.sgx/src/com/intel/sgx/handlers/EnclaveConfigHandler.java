///////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Intel Corporation.				 //
// 									 //
// All rights reserved. This program and the accompanying materials	 //
// are made available under the terms of the Eclipse Public License v1.0 //
// which accompanies this distribution, and is available at		 //
// http://www.eclipse.org/legal/epl-v10.html				 //
// 									 //
// Contributors:							 //
//     Intel Corporation - initial implementation and documentation	 //
///////////////////////////////////////////////////////////////////////////


package com.intel.sgx.handlers;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.cdt.core.templateengine.TemplateCore;
import org.eclipse.cdt.core.templateengine.TemplateEngine;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.intel.sgx.Activator;
import com.intel.sgx.dialogs.EnclaveConfigDialog;
import com.intel.sgx.dialogs.SGXDialogBase;

public class EnclaveConfigHandler extends SGXHandler {

	public String prodId;
	public String isvSvn;
	public String threadStackSize;
	public String globalHeapSize;
	public String tcsNum;
	public String tcsPolicy;
	public String disableDebug;
	private IFile configPath;

	@Override
	public void addHandlerListener(IHandlerListener arg0) {
	}

	@Override
	public void dispose() {
	}

	@Override
	public Object executeSGXStuff() throws CancelException, ErrorException {

		FilteredResourcesSelectionDialog d = SGXDialogBase
				.dialogForConfig(shell);
		d.setTitle("Select Config File");
		if (d.open() != Dialog.OK) {
			cancel();
		}

		configPath = ((IFile) d.getResult()[0]);

		readConfig(configPath.getLocation());
		EnclaveConfigDialog dialog = new EnclaveConfigDialog(shell, this);
		if (dialog.open() != InputDialog.OK) {
			return null;
		}
		writeConfig();
		refreshProject();

		return null;
	}

	protected void writeConfig() {
		IProgressMonitor monitor = new NullProgressMonitor();
		TemplateCore template = TemplateEngine.getDefault().getTemplateById("SGXEnclaveConfig");
		
		Map<String, String> valueStore = template.getValueStore();
		valueStore.put("projectName", project.getName());
		valueStore.put("configFile", configPath.getProjectRelativePath().toOSString());
		valueStore.put("ProdID", this.prodId);
		valueStore.put("IsvSvn", this.isvSvn);
		valueStore.put("ThreadStackSize", this.threadStackSize);
		valueStore.put("GlobalHeapSize", this.globalHeapSize);
		valueStore.put("TcsNumber", this.tcsNum);
		valueStore.put("TcsPolicy", this.tcsPolicy);
		valueStore.put("DisableDebug", this.disableDebug);
		IStatus[] result = template.executeTemplateProcesses(monitor, true);
		
		for (IStatus status: result) {
		}
	}
	
	

	protected void readConfig(IPath configPath) throws ErrorException {

		try {
			String xmlFile = configPath.toString();
			File configFile = new File(xmlFile);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder;
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(configFile);
			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName("EnclaveConfiguration");
			Node nNode = nList.item(0);

			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				Element e = (Element) nNode;
				this.prodId = e.getElementsByTagName("ProdID").item(0)
						.getTextContent();
				this.isvSvn = e.getElementsByTagName("ISVSVN").item(0)
						.getTextContent();
				this.threadStackSize = e.getElementsByTagName("StackMaxSize")
						.item(0).getTextContent();
				this.globalHeapSize = e.getElementsByTagName("HeapMaxSize")
						.item(0).getTextContent();
				this.tcsNum = e.getElementsByTagName("TCSNum").item(0)
						.getTextContent();
				this.tcsPolicy = e.getElementsByTagName("TCSPolicy").item(0)
						.getTextContent();
				this.disableDebug = e.getElementsByTagName("DisableDebug")
						.item(0).getTextContent();
			}

		} catch (ParserConfigurationException e) {
			Activator.log(e);
			e.printStackTrace();
			quitWithError("Could not parse '"+configPath.toOSString()+"'");
		} catch (SAXException e) {
			Activator.log(e);
			e.printStackTrace();
			quitWithError("Could not parse '"+configPath.toOSString()+"'");
		} catch (IOException e) {
			Activator.log(e);
			e.printStackTrace();
			quitWithError("Could not read'"+configPath.toOSString()+"'");
		}

	}
}
