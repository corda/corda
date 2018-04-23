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
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.eclipse.cdt.core.templateengine.TemplateCore;
import org.eclipse.cdt.core.templateengine.TemplateEngine;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
//import org.eclipse.core.resources.IWorkspaceRunnable;
//import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
//import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import com.intel.sgx.Activator;
import com.intel.sgx.natures.SGXNature;

public class AddSGXNature implements IHandler {

	private IProject project; 

	public AddSGXNature() {
		project = null;
	}

	@Override
	public void addHandlerListener(IHandlerListener arg0) {
	}

	@Override
	public void dispose() {
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
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
		} else {
			try {
				TemplateCore template = TemplateEngine.getDefault().getTemplateById("AddSGXNature");
			
				Map<String,String> valueStore = template.getValueStore();
				valueStore.put("projectName",project.getName());
				valueStore.put("baseName",project.getName());
				
				IProgressMonitor  monitor = new NullProgressMonitor();
				template.executeTemplateProcesses(monitor, false);
		
				IWorkspace workspace = ResourcesPlugin.getWorkspace();
				try{
					IProjectDescription description = project.getDescription();
					String[] natures = description.getNatureIds();

					String[] newNatures = new String[natures.length + 1];
					System.arraycopy(natures, 0, newNatures, 0, natures.length);
					newNatures[natures.length] = SGXNature.NATURE_ID;
					IStatus status = workspace.validateNatureSet(newNatures);

					if (status.getCode() == IStatus.OK) {
						description.setNatureIds(newNatures);
						project.setDescription(description, null);
					}

					project.refreshLocal(IResource.DEPTH_ONE,null);
				} catch(CoreException e){
					Activator.log(e);
					throw new InvocationTargetException(e);
				}	
			}  catch(InvocationTargetException e){
				Activator.log(e);
				e.printStackTrace();
			}

			try {
				project.refreshLocal(IResource.DEPTH_INFINITE,null);
			} catch (CoreException e) {
				e.printStackTrace();
			}
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
	public void removeHandlerListener(IHandlerListener arg0) {
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
