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

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.intel.sgx.Activator;
import com.intel.sgx.dialogs.AddUntrustedModuleDialog;
import com.intel.sgx.preferences.PreferenceConstants;

public class AddUntrustedModule extends ModuleCreationBaseHandler implements IHandler {

	public String edlFilename = "";
	public String libPath = "";
	private IPath edlCanonicalFilename;
	private IPath libCanonicalPathname;

	@Override
	public void addHandlerListener(IHandlerListener handlerListener) {
	}

	@Override
	public void dispose() {
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		String edlBasename,edlPath,linuxLibPath,modulePath,enclaveBasename;
		IProject project = null;
		
		//Display display = Display.getCurrent();
		Shell shell = null;

		AddUntrustedModuleDialog dialog = new AddUntrustedModuleDialog(shell, this);
		if (dialog.open() != InputDialog.OK) {
			return null;
		}

		if((edlFilename.isEmpty() && libPath.isEmpty())){
			System.err.println("No Enclave selected to Import.");
			return null;
		}
		if( edlFilename.isEmpty() ){
			System.err.println("Edl File not selected.");
			return null;
		}
		edlCanonicalFilename = Path.fromOSString(edlFilename);
		if (!edlCanonicalFilename.getFileExtension().toLowerCase().equals("edl")) {
			System.err.println("Error: EDL file extension = "+ edlCanonicalFilename.getFileExtension());
			return null;
		}
		edlBasename = edlCanonicalFilename.removeFileExtension().lastSegment();

				
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

		IPath targetRelPath = project.getProjectRelativePath().append("sgx").append("untrusted_" + edlBasename);
		
		try {
			for (int i = 1; i <= targetRelPath.segmentCount(); i++) {
				IFolder subfolder = project.getFolder(targetRelPath.uptoSegment(i));

				if (!subfolder.exists()) {
					subfolder.create(true, true, null);
				}
			}
		} catch (CoreException e) {
			System.err.println("Error:  Error creating enclave directory.");
			Activator.log(e);
			e.printStackTrace();
		}

		IPath edlRelPath = (Path.fromOSString(edlFilename)).makeRelativeTo(project.getLocation().append("sgx"));
		edlPath = edlRelPath.toOSString();
		IPath linuxLibRelPath = (Path.fromOSString(libPath)).makeRelativeTo(project.getLocation().append("sgx"));
		
		if(linuxLibRelPath.removeLastSegments(1).lastSegment().toString().equalsIgnoreCase("sgx")){
			linuxLibPath = linuxLibRelPath.removeLastSegments(3).toOSString();
			modulePath = linuxLibRelPath.removeFirstSegments(linuxLibRelPath.segmentCount()-3).removeLastSegments(1).toOSString();
		}
		else if(linuxLibRelPath.removeLastSegments(1).lastSegment().toString().equalsIgnoreCase("src")){
			linuxLibPath = linuxLibRelPath.removeLastSegments(3).toOSString();
			modulePath = linuxLibRelPath.removeFirstSegments(linuxLibRelPath.segmentCount()-3).removeLastSegments(1).toOSString();
		}
		else{
			linuxLibPath = linuxLibRelPath.removeLastSegments(2).toOSString();
			modulePath = linuxLibRelPath.removeFirstSegments(linuxLibRelPath.segmentCount()-2).removeLastSegments(1).toOSString();
		}
		
		IProgressMonitor monitor = new NullProgressMonitor();
		TemplateCore template = null;
		if(isCPProject(project))
			  template = TemplateEngine.getDefault().getTemplateById("SGXUntrustedModuleC++Minimal");
		else
			  template = TemplateEngine.getDefault().getTemplateById("SGXUntrustedModuleCMinimal");
			
				

		Map<String, String> valueStore = template.getValueStore();
		
					
		valueStore.put("projectName", project.getName());
		valueStore.put("sourcepath",linuxLibPath);
		valueStore.put("edlPath", edlPath);
		valueStore.put("baseName", edlBasename);
		valueStore.put("workspacePath", linuxLibPath);//deprecate
		valueStore.put("modPath", modulePath);
        valueStore.put("ENCLAVENAME",edlBasename.toUpperCase());
        valueStore.put("libPath",libPath);
        valueStore.put("SdkPathFromPlugin", Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.SDK_PATH));

		IStatus[] statuses =  template.executeTemplateProcesses(monitor, false);
		
		try {
			copyFile(new File(edlFilename), project.getLocation().append("sgx").append("untrusted_"+edlBasename).append(edlBasename+".edl").toFile());
		} catch (IOException e1) {
			e1.printStackTrace();
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
