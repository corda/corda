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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import org.eclipse.cdt.core.templateengine.TemplateCore;
import org.eclipse.cdt.core.templateengine.TemplateEngine;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.intel.sgx.Activator;
import com.intel.sgx.dialogs.AddEnclaveFileDialog;
import com.intel.sgx.preferences.PreferenceConstants;

public class AddEnclave extends ModuleCreationBaseHandler implements IHandler  {

	public String edlFilename = "";
	public String linuxMakePath = "";
	@Override
	public void addHandlerListener(IHandlerListener handlerListener) {
	}

	@Override
	public void dispose() {
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		String edlBasename,linuxPath,enclaveBasename;
		IProject project = null;
		
		// Display display = Display.getCurrent();
		// Shell shell = new Shell(display);
		Shell shell = null;
		AddEnclaveFileDialog dialog = new AddEnclaveFileDialog(shell, this);
		if (dialog.open() != InputDialog.OK) {
			return null;
		}

		if((edlFilename.isEmpty())){
			System.err.println("No Enclave selected to Import.");
			return null;
		}

		edlBasename = edlFilename;
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		Object element = null;
		if (selection instanceof IStructuredSelection) {
			element = ((IStructuredSelection) selection).getFirstElement();
			if (element instanceof IResource) {
				project = ((IResource) element).getProject();
			}
		}
		if (!project.exists()) {
			System.err.println("Error:  Project not found");
			return null;
		}
		
		for (IConfigurationElement i : Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.core.resources.projectNature")){
		}
		
		
		
		
		IPath linuxMkRelPath = (Path.fromOSString(linuxMakePath)).makeRelativeTo(project.getLocation().append("sgx").append("enclave_"+edlBasename));

		
		if(linuxMkRelPath.removeLastSegments(1).lastSegment().toString().equalsIgnoreCase("sgx")){
			linuxPath = linuxMkRelPath.removeLastSegments(3).toOSString();
			enclaveBasename = linuxMkRelPath.removeLastSegments(2).lastSegment().toString();
		}
		
		else{
			linuxPath = linuxMkRelPath.removeLastSegments(2).toOSString();
			enclaveBasename = linuxMkRelPath.removeLastSegments(1).lastSegment().toString();
		}
		
		IProgressMonitor monitor = new NullProgressMonitor();
		TemplateCore template = null;
        if(isCPProject(project))
    		if(dialog.generateApp())
    		{
	    		template = TemplateEngine.getDefault().getTemplateById("SGXEnclaveC++WithSample");
    		}
    		else
    		{
	    		template = TemplateEngine.getDefault().getTemplateById("SGXEnclaveC++Minimal");
    		}
        else
    		if(dialog.generateApp())
    		{
	    		template = TemplateEngine.getDefault().getTemplateById("SGXEnclaveCWithSample");
    		}
    		else
    		{
	    		template = TemplateEngine.getDefault().getTemplateById("SGXEnclaveCMinimal");
    		}
		
		Map<String, String> valueStore = template.getValueStore();
		valueStore.put("projectName", project.getName());
		valueStore.put("workspacePath", linuxPath);
		valueStore.put("baseName", enclaveBasename);
		valueStore.put("enclaveName",edlFilename);
		valueStore.put("EnclaveName",capitalize(edlFilename));
		valueStore.put("ENCLAVENAME",edlFilename.toUpperCase());
		valueStore.put("SdkPathFromPlugin", Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.SDK_PATH));
		
		IStatus[] statuses =  template.executeTemplateProcesses(monitor, false);
		for(IStatus e: statuses)
		{
		}

		ManagedBuildManager.saveBuildInfo(project, true);
		try {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (CoreException e) {
			Activator.log(e);
			e.printStackTrace();
		}
		
		
		return null;
	}
	
	private String capitalize(final String line) {
		   return Character.toUpperCase(line.charAt(0)) + line.substring(1);
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public boolean isHandled() {
		return true;
	}

	@Override
	public void removeHandlerListener(IHandlerListener handlerListener) {
	}

	public void setFilename(String filename) {
		edlFilename = filename;
	}
	
	
	

	public static void copyFile(File source, File dest) throws IOException {
		byte[] bytes = new byte[4092];
		if (source != null && dest != null) {
			if (source.isFile()) {
				FileInputStream in = null;
				FileOutputStream out = null;
				try {
					in = new FileInputStream(source);
					out = new FileOutputStream(dest);
					int len;
					while ((len = in.read(bytes)) != -1) {
						out.write(bytes, 0, len);
					}
				} catch (Exception e) {
					Activator.log(e);
					System.err.println("Error: " + e.toString());
				} finally {
					try {
						if (in != null)
							in.close();
					} finally {
						if (out != null)
							out.close();
					}
				}
			}
		}
	}

}
